package no.unit.nva.doi.requests.service.impl;

import static java.util.Objects.nonNull;
import static nva.commons.utils.attempt.Try.attempt;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Index;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.KeyAttribute;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.RangeKeyCondition;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.internal.IteratorSupport;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import no.unit.nva.doi.requests.contants.ServiceConstants;
import no.unit.nva.doi.requests.exception.DynamoDBException;
import no.unit.nva.doi.requests.api.model.requests.CreateDoiRequest;
import no.unit.nva.doi.requests.api.model.responses.DoiRequestSummary;
import no.unit.nva.doi.requests.service.DoiRequestsService;
import no.unit.nva.model.DoiRequest;
import no.unit.nva.model.DoiRequestMessage;
import no.unit.nva.model.DoiRequestStatus;
import no.unit.nva.model.Publication;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.exceptions.ForbiddenException;
import nva.commons.exceptions.commonexceptions.ConflictException;
import nva.commons.exceptions.commonexceptions.NotFoundException;
import nva.commons.utils.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamoDBDoiRequestsService implements DoiRequestsService {

    public static final String PUBLICATION_ID_HASH_KEY_NAME = "identifier";
    public static final String DOI_REQUEST_STATUS_DATE = "doiRequestStatusDate";
    public static final String PUBLISHER_ID = "publisherId";
    public static final String ERROR_READING_FROM_TABLE = "Error reading from table";
    public static final int SINGLE_ITEM = 1;
    public static final String WRONG_OWNER_ERROR =
        "User with username %s not allowed to create a DoiRequest for publication owned by %s";
    public static final String ERROR_MESSAGE_PUBLICATION_WITH_IDENTIFIER_S_NOT_FOUND = "Publication with identifier "
        + "%s not found.";

    private final Logger logger = LoggerFactory.getLogger(DynamoDBDoiRequestsService.class);
    private final Table publicationsTable;
    private final Index doiRequestsIndex;
    private final ObjectMapper objectMapper;
    private final Clock clockForTimestamps;

    /**
     * Constructor for DynamoDBDoiRequestsService.
     *
     * @param objectMapper objectMapper
     * @param table        DynamoDB table
     * @param index        DynamoDB index
     */
    public DynamoDBDoiRequestsService(ObjectMapper objectMapper, Table table, Index index) {
        this.objectMapper = objectMapper;
        this.publicationsTable = table;
        this.doiRequestsIndex = index;
        this.clockForTimestamps = Clock.systemDefaultZone();
    }

    public DynamoDBDoiRequestsService(AmazonDynamoDB client,
                                      ObjectMapper objectMapper,
                                      Environment environment) {
        this(client, objectMapper, environment, Clock.systemDefaultZone());
    }

    public DynamoDBDoiRequestsService(AmazonDynamoDB client,
                                      ObjectMapper objectMapper,
                                      Environment environment,
                                      Clock clockForTimestamps) {

        DynamoDB dynamoDB = new DynamoDB(client);
        this.publicationsTable = dynamoDB
            .getTable(environment.readEnv(ServiceConstants.PUBLICATIONS_TABLE_NAME_ENV_VARIABLE));
        this.doiRequestsIndex = publicationsTable
            .getIndex(environment.readEnv(ServiceConstants.DOI_REQUESTS_INDEX_ENV_VARIABLE));
        this.objectMapper = objectMapper;
        this.clockForTimestamps = clockForTimestamps;
    }

    @Override
    public List<DoiRequestSummary> findDoiRequestsByStatus(URI publisher, DoiRequestStatus status)
        throws ApiGatewayException {
        RangeKeyCondition rangeKeyCondition = new RangeKeyCondition(DOI_REQUEST_STATUS_DATE);
        rangeKeyCondition.beginsWith(status.toString());
        ItemCollection<QueryOutcome> outcome;
        try {
            outcome = doiRequestsIndex.query(PUBLISHER_ID, publisher.toString(), rangeKeyCondition);
        } catch (Exception e) {
            throw new DynamoDBException(ERROR_READING_FROM_TABLE, e);
        }
        return parseJsonToDoiRequestSummaries(outcome);
    }

    @Override
    public List<DoiRequestSummary> findDoiRequestsByStatusAndOwner(URI publisher, DoiRequestStatus status, String owner)
        throws ApiGatewayException {
        return findDoiRequestsByStatus(publisher, status)
            .stream()
            .filter(doiRequestSummary -> doiRequestSummary.getPublicationOwner().equals(owner))
            .collect(Collectors.toList());
    }

    @Override
    public Optional<DoiRequestSummary> fetchDoiRequest(UUID publicationId) throws NotFoundException {
        Publication publication = fetchPublication(publicationId);
        return Optional.of(DoiRequestSummary.fromPublication(publication));
    }

    @Override
    public void createDoiRequest(CreateDoiRequest createDoiRequest, String username)
        throws ConflictException, NotFoundException, ForbiddenException {

        Publication publication = fetchPublicationForUser(createDoiRequest, username);
        verifyThatPublicationHasNoPreviousDoiRequest(publication);

        var newDoiRequestEntry = createDoiRequestEntry(createDoiRequest, username);
        publication.setDoiRequest(newDoiRequestEntry);

        putItem(publication);
    }

    @Override
    public void updateDoiRequest(UUID publicationID, DoiRequestStatus requestedStatusChange, String requestedByUsername)
        throws NotFoundException, ForbiddenException {
        Publication publication = fetchPublication(publicationID);
        validateUsername(publication, requestedByUsername);
        publication.updateDoiRequestStatus(requestedStatusChange);
        putItem(publication);
    }

    private Publication fetchPublicationForUser(CreateDoiRequest createDoiRequest, String username)
        throws NotFoundException, ForbiddenException {
        var publication = fetchPublication(UUID.fromString(createDoiRequest.getPublicationId()));
        validateUsername(publication, username);
        return publication;
    }

    protected Optional<DoiRequestSummary> toDoiRequestSummary(Item item) {
        DoiRequestSummary doiRequestSummary = null;
        try {
            doiRequestSummary = objectMapper.readValue(item.toJSON(), DoiRequestSummary.class);
        } catch (JsonProcessingException e) {
            logger.info("Error mapping Item to DoiRequestSummary", e);
        }
        return Optional.ofNullable(doiRequestSummary);
    }

    private DoiRequest createDoiRequestEntry(CreateDoiRequest createDoiRequest, String username) {
        return createDoiRequest.getMessage()
            .map(message -> doiRequestBuilderWithMessage(message, username))
            .orElse(doiRequestBuilderWithoutMessage())
            .build();
    }

    private void verifyThatPublicationHasNoPreviousDoiRequest(Publication publication) throws ConflictException {
        if (nonNull(publication.getDoiRequest())) {
            throw new ConflictException(DOI_ALREADY_EXISTS_ERROR + publication.getIdentifier().toString());
        }
    }

    private DoiRequest.Builder doiRequestBuilderWithoutMessage() {
        return new DoiRequest.Builder()
            .withStatus(DoiRequestStatus.REQUESTED)
            .withDate(Instant.now(clockForTimestamps));
    }

    private DoiRequest.Builder doiRequestBuilderWithMessage(String message, String username) {
        return doiRequestBuilderWithoutMessage().addMessage(createMessage(message, username));
    }

    private void validateUsername(Publication publication, String username) throws ForbiddenException {
        if (!(publication.getOwner().equals(username))) {
            logger.warn(String.format(WRONG_OWNER_ERROR, username, publication.getOwner()));
            throw new ForbiddenException();
        }
    }

    private DoiRequestMessage createMessage(String message, String author) {
        return new DoiRequestMessage.Builder()
            .withAuthor(author)
            .withText(message)
            .withTimestamp(Instant.now(clockForTimestamps))
            .build();
    }

    private void putItem(Publication publication) {
        Item item = publicationToItem(publication);
        PutItemSpec putItemSpec =
            new PutItemSpec().withItem(item);

        publicationsTable.putItem(putItemSpec);
    }

    private Publication fetchPublication(UUID publicationId) throws NotFoundException {
        QuerySpec query = buildQuery(publicationId);
        return executeQuery(query)
            .map(this::itemToPublication)
            .orElseThrow(() -> new NotFoundException(String.format(
                ERROR_MESSAGE_PUBLICATION_WITH_IDENTIFIER_S_NOT_FOUND,
                publicationId.toString())));
    }

    private Publication itemToPublication(Item item) {
        return objectMapper.convertValue(item.asMap(), Publication.class);
    }

    private Item publicationToItem(Publication publication) {
        String serialized = attempt(() -> objectMapper.writeValueAsString(publication))
            .orElseThrow(fail -> new RuntimeException(fail.getException()));
        return Item.fromJSON(serialized);
    }

    private Optional<Item> executeQuery(QuerySpec query) {
        ItemCollection<QueryOutcome> result = publicationsTable.query(query);
        return extractSingleItemFromResult(result);
    }

    private Optional<Item> extractSingleItemFromResult(ItemCollection<QueryOutcome> result) {

        IteratorSupport<Item, QueryOutcome> iterator = result.iterator();
        if (iterator.hasNext()) {
            return Optional.of(iterator.next());
        }
        return Optional.empty();
    }

    private QuerySpec buildQuery(UUID publicationId) {
        QuerySpec query = new QuerySpec()
            .withHashKey(new KeyAttribute(PUBLICATION_ID_HASH_KEY_NAME, publicationId.toString()))
            .withScanIndexForward(false)
            .withMaxResultSize(SINGLE_ITEM);
        return query;
    }

    private List<DoiRequestSummary> parseJsonToDoiRequestSummaries(ItemCollection<QueryOutcome> items) {
        List<DoiRequestSummary> doiRequestSummaries = new ArrayList<>();
        items.forEach(item -> toDoiRequestSummary(item).ifPresent(doiRequestSummaries::add));
        return doiRequestSummaries;
    }
}

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
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.internal.IteratorSupport;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.unit.nva.doi.requests.api.model.requests.CreateDoiRequest;
import no.unit.nva.doi.requests.contants.ServiceConstants;
import no.unit.nva.doi.requests.exception.DynamoDBException;
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
import nva.commons.utils.JsonUtils;
import nva.commons.utils.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamoDBDoiRequestsService implements DoiRequestsService {

    public static final String PUBLICATION_ID_HASH_KEY_NAME = "identifier";

    public static final String PUBLISHER_ID = "publisherId";
    public static final String ERROR_READING_FROM_TABLE = "Error reading from table";
    public static final int SINGLE_ITEM = 1;
    public static final String WRONG_OWNER_ERROR =
        "User with username %s not allowed to create a DoiRequest for publication owned by %s";
    public static final String PUBLICATION_NOT_FOUND_ERROR_MESSAGE = "Could not find publication: ";
    public static final String ACCESS_DENIED_ERROR_MESSAGE = "Status Code: 400; Error Code: AccessDeniedException";

    private final Logger logger = LoggerFactory.getLogger(DynamoDBDoiRequestsService.class);
    private final Clock clockForTimestamps;
    private final ObjectMapper objectMapper;

    private final Table publicationsTable;
    private final Index doiRequestsIndex;

    /**
     * Constructor for DynamoDBDoiRequestsService.
     *
     * @param table DynamoDB table
     * @param index DynamoDB index
     */
    public DynamoDBDoiRequestsService(Table table, Index index) {
        this.objectMapper = JsonUtils.objectMapper;
        this.publicationsTable = table;
        this.doiRequestsIndex = index;
        this.clockForTimestamps = Clock.systemDefaultZone();
    }

    protected DynamoDBDoiRequestsService(AmazonDynamoDB client, Environment environment, Clock clockForTimestamps) {

        this.clockForTimestamps = clockForTimestamps;
        this.objectMapper = JsonUtils.objectMapper;

        DynamoDB dynamoDB = new DynamoDB(client);
        final var tableName = environment.readEnv(ServiceConstants.PUBLICATIONS_TABLE_NAME_ENV_VARIABLE);
        final var indexName = environment.readEnv(ServiceConstants.DOI_REQUESTS_INDEX_ENV_VARIABLE);
        this.publicationsTable = dynamoDB.getTable(tableName);

        this.doiRequestsIndex = publicationsTable.getIndex(indexName);
    }

    @Override
    public List<Publication> findDoiRequestsByStatus(URI publisher, DoiRequestStatus status)
        throws ApiGatewayException {
        return
            extractMostRecentVersionOfEachPublication(publisher)
                .stream()
                .filter(publication -> hasDoiRequestStatus(publication, status))
                .collect(Collectors.toList());
    }

    //TODO : Look at issue NP-1823:Getting doi requests for a user cannot be secured
    @Override
    public List<Publication> findDoiRequestsByStatusAndOwner(URI publisher, DoiRequestStatus status, String owner)
        throws ApiGatewayException {
        return findDoiRequestsByStatus(publisher, status)
            .stream()
            .parallel()
            .filter(publication -> belongsToUser(owner, publication))
            .collect(Collectors.toList());
    }

    @Override
    public Optional<Publication> fetchDoiRequestByPublicationIdentifier(UUID publicationIdentifier)
        throws NotFoundException {
        Publication publication = fetchPublicationByIdentifier(publicationIdentifier);
        return Optional.of(publication);
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
    public void updateDoiRequest(UUID publicationIdentifier, DoiRequestStatus requestedStatusChange,
                                 String requestedByUsername)
        throws NotFoundException, ForbiddenException {
        Publication publication = fetchPublicationByIdentifier(publicationIdentifier);
        validateUsername(publication, requestedByUsername);
        publication.updateDoiRequestStatus(requestedStatusChange);
        putItem(publication);
    }

    private List<Publication> extractMostRecentVersionOfEachPublication(URI publisher) throws ApiGatewayException {
        return attempt(() -> queryByPublisher(publisher))
            .map(this::extractPublications)
            .map(this::keepMostRecentPublications)
            .orElseThrow(this::handleErrorFetchingPublications);
    }

    private boolean hasDoiRequestStatus(Publication publication, DoiRequestStatus desiredStatus) {
        return Optional.of(publication)
            .map(Publication::getDoiRequest)
            .map(DoiRequest::getStatus)
            .filter(desiredStatus::equals)
            .isPresent();
    }

    private List<Publication> keepMostRecentPublications(List<Publication> publications) {
        return publications.stream()
            .parallel()
            .collect(Collectors.groupingBy(Publication::getIdentifier))
            .values()
            .stream()
            .flatMap(this::mostRecentPublication)
            .collect(Collectors.toList());
    }

    private Stream<Publication> mostRecentPublication(List<Publication> publicationList) {
        return publicationList
            .stream()
            .max(Comparator.comparing(Publication::getModifiedDate))
            .stream();
    }

    private boolean belongsToUser(String owner, Publication publication) {
        return nonNull(publication.getOwner()) && publication.getOwner().equals(owner);
    }

    private ItemCollection<QueryOutcome> queryByPublisher(URI publisher) {
        QuerySpec querySpec = new QuerySpec().withHashKey(PUBLISHER_ID, publisher.toString());
        return doiRequestsIndex.query(querySpec);
    }

    private <T> ApiGatewayException handleErrorFetchingPublications(Failure<T> fail) {
        Exception exception = fail.getException();
        if (exception instanceof AmazonDynamoDBException) {
            String message = exception.getMessage();
            if (message.contains(ACCESS_DENIED_ERROR_MESSAGE)) {
                return new ForbiddenException();
            }
        }
        return new DynamoDBException(ERROR_READING_FROM_TABLE, fail.getException());
    }

    private List<Publication> extractPublications(ItemCollection<QueryOutcome> outcome) {
        List<Publication> publications = new ArrayList<>();
        for (Item item : outcome) {
            Publication publication = itemToPublication(item);
            publications.add(publication);
        }
        return publications;
    }

    private Publication fetchPublicationForUser(CreateDoiRequest createDoiRequest, String username)
        throws NotFoundException, ForbiddenException {
        var publication = fetchPublicationByIdentifier(UUID.fromString(createDoiRequest.getPublicationId()));
        validateUsername(publication, username);
        return publication;
    }

    private DoiRequest createDoiRequestEntry(CreateDoiRequest createDoiRequest, String username) {
        return createDoiRequest.getMessage()
            .map(message -> doiRequestBuilderWithMessage(message, username))
            .orElse(doiRequestBuilderWithoutMessage())
            .build();
    }

    private DoiRequest.Builder doiRequestBuilderWithMessage(String message, String username) {
        return doiRequestBuilderWithoutMessage().addMessage(createMessage(message, username));
    }

    private DoiRequest.Builder doiRequestBuilderWithoutMessage() {
        return new DoiRequest.Builder()
            .withStatus(DoiRequestStatus.REQUESTED)
            .withDate(Instant.now(clockForTimestamps));
    }

    private void verifyThatPublicationHasNoPreviousDoiRequest(Publication publication) throws ConflictException {
        if (nonNull(publication.getDoiRequest())) {
            throw new ConflictException(DOI_ALREADY_EXISTS_ERROR + publication.getIdentifier().toString());
        }
    }

    private void validateUsername(Publication publication, String username) throws ForbiddenException {
        if (!(belongsToUser(username, publication))) {
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
        PutItemSpec putItemSpec = new PutItemSpec().withItem(item);
        publicationsTable.putItem(putItemSpec);
    }

    private Publication fetchPublicationByIdentifier(UUID publicationIdentifier) throws NotFoundException {
        return Optional.of(queryLatestPublication(publicationIdentifier))
            .flatMap(this::executeQuery)
            .map(this::itemToPublication)
            .orElseThrow(() -> handlePublicationNotFoundError(publicationIdentifier));
    }

    private NotFoundException handlePublicationNotFoundError(UUID publicationIdentifier) {
        logger.error(PUBLICATION_NOT_FOUND_ERROR_MESSAGE + publicationIdentifier.toString());
        return new NotFoundException(PUBLICATION_NOT_FOUND_ERROR_MESSAGE + publicationIdentifier.toString());
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

    private QuerySpec queryLatestPublication(UUID publicationIdentifier) {
        return new QuerySpec()
            .withHashKey(new KeyAttribute(PUBLICATION_ID_HASH_KEY_NAME, publicationIdentifier.toString()))
            .withScanIndexForward(false)
            .withMaxResultSize(SINGLE_ITEM);
    }
}

package no.unit.nva.doi.requests.service.impl;

import static java.util.Objects.nonNull;
import static no.unit.nva.useraccessmanagement.dao.AccessRight.APPROVE_DOI_REQUEST;
import static no.unit.nva.useraccessmanagement.dao.AccessRight.REJECT_DOI_REQUEST;
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
import no.unit.nva.doi.requests.exception.BadRequestException;
import no.unit.nva.doi.requests.exception.DynamoDBException;
import no.unit.nva.doi.requests.model.ApiUpdateDoiRequest;
import no.unit.nva.doi.requests.service.DoiRequestsService;
import no.unit.nva.model.DoiRequest;
import no.unit.nva.model.DoiRequestMessage;
import no.unit.nva.model.DoiRequestMessage.Builder;
import no.unit.nva.model.DoiRequestStatus;
import no.unit.nva.model.Publication;
import no.unit.nva.model.PublicationStatus;
import no.unit.nva.useraccessmanagement.dao.AccessRight;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.exceptions.ForbiddenException;
import nva.commons.exceptions.commonexceptions.ConflictException;
import nva.commons.exceptions.commonexceptions.NotFoundException;
import nva.commons.utils.Environment;
import nva.commons.utils.JsonUtils;
import nva.commons.utils.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("PMD.GodClass")
//TODO: Fix GodClass problem (NP-2007)
public class DynamoDBDoiRequestsService implements DoiRequestsService {

    public static final String PUBLICATION_ID_HASH_KEY_NAME = "identifier";
    public static final String DOI_ALREADY_EXISTS_ERROR = "DoiRequest already exists for publication: ";

    public static final String PUBLISHER_ID = "publisherId";
    public static final String ERROR_READING_FROM_TABLE = "Error reading from table";
    public static final int SINGLE_ITEM = 1;
    public static final String WRONG_OWNER_ERROR =
        "User with username %s not allowed to create a DoiRequest for publication owned by %s";
    public static final String PUBLICATION_NOT_FOUND_ERROR_MESSAGE = "Could not find publication: ";
    public static final String ACCESS_DENIED_ERROR_MESSAGE = "Status Code: 400; Error Code: AccessDeniedException";
    public static final String USER_NOT_ALLOWED_TO_APPROVE_DOI_REQUEST = "User not allowed to approve a DOI request: ";
    public static final String USER_NOT_ALLOWED_TO_REJECT_A_DOI_REQUEST = "User is not allowed to reject a Doi request";

    public static final String ERROR_MESSAGE_UPDATE_DOIREQUEST_MISSING_DOIREQUEST =
        "You must initiate creation of a DoiRequest before you can update it.";

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
        throws ApiGatewayException {

        Publication publication = fetchPublicationForUser(createDoiRequest, username);
        verifyThatPublicationHasNoPreviousDoiRequest(publication);
        DoiRequest newDoiRequestEntry = createDoiRequestEntry(createDoiRequest, username);
        updatePublication(publication, newDoiRequestEntry);
        putItem(publication);
    }

    @Override
    public void updateDoiRequest(UUID publicationIdentifier, ApiUpdateDoiRequest apiUpdateDoiRequest,
                                 String requestedByUsername, List<AccessRight> userAccessRights)
        throws ApiGatewayException {

        authorizeChange(apiUpdateDoiRequest.getDoiRequestStatus(), userAccessRights, requestedByUsername);

        Publication publication = fetchPublicationByIdentifier(publicationIdentifier);

        DoiRequest updatedDoiRequest = updateDoiRequest(publication, apiUpdateDoiRequest, requestedByUsername);

        updatePublication(publication, updatedDoiRequest);
        putItem(publication);
    }

    private DoiRequest updateDoiRequest(Publication publication, ApiUpdateDoiRequest apiUpdateDoiRequest,
                                        String requestedByUsername) throws BadRequestException {
        Instant currentTime = clockForTimestamps.instant();

        DoiRequest.Builder updatedDoiRequestBuilder = updateDoiRequestStatus(apiUpdateDoiRequest, publication,
            currentTime);

        createDoiRequestMessage(apiUpdateDoiRequest, requestedByUsername, currentTime)
            .ifPresent(updatedDoiRequestBuilder::addMessage);

        return updatedDoiRequestBuilder.build();
    }

    private void updatePublication(Publication publication, DoiRequest updatedDoiRequest) {
        publication.setDoiRequest(updatedDoiRequest);
        publication.setModifiedDate(updatedDoiRequest.getModifiedDate());
    }



    private Optional<DoiRequestMessage> createDoiRequestMessage(ApiUpdateDoiRequest apiUpdateDoiRequest,
                                                                String requestedByUsername, Instant now) {
        return apiUpdateDoiRequest.getMessage()
            .map(messageText -> new Builder()
                .withAuthor(requestedByUsername)
                .withText(messageText)
                .withTimestamp(now)
                .build());
    }

    private DoiRequest.Builder updateDoiRequestStatus(ApiUpdateDoiRequest apiUpdateDoiRequest,
                                                      Publication publication,
                                                      Instant now) throws BadRequestException {
        return Optional.ofNullable(publication.getDoiRequest())
            .map(DoiRequest::copy)
            .map(builder -> builder.withStatus(apiUpdateDoiRequest.getDoiRequestStatus()))
            .map(builder -> builder.withModifiedDate(now))
            .orElseThrow(() -> new BadRequestException(ERROR_MESSAGE_UPDATE_DOIREQUEST_MISSING_DOIREQUEST));
    }

    private void authorizeChange(DoiRequestStatus requestedStatusChange,
                                 List<AccessRight> userAccessRights,
                                 String username)
        throws ForbiddenException {

        if (userTriesToApproveDoiRequest(requestedStatusChange)
            && userDoesNotHaveTheRight(userAccessRights, APPROVE_DOI_REQUEST)) {
            logger.warn(USER_NOT_ALLOWED_TO_APPROVE_DOI_REQUEST + username);
            throw new ForbiddenException();
        }

        if (userTriesToRejectDoiRequest(requestedStatusChange)
            && userDoesNotHaveTheRight(userAccessRights, REJECT_DOI_REQUEST)) {

            logger.warn(USER_NOT_ALLOWED_TO_REJECT_A_DOI_REQUEST + username);
            throw new ForbiddenException();
        }
    }

    private boolean userTriesToRejectDoiRequest(DoiRequestStatus requestedStatusChange) {
        return DoiRequestStatus.REJECTED.equals(requestedStatusChange);
    }

    private boolean userDoesNotHaveTheRight(List<AccessRight> userAccessRights, AccessRight rejectDoiRequest) {
        return !userAccessRights.contains(rejectDoiRequest);
    }

    private boolean userTriesToApproveDoiRequest(DoiRequestStatus requestedStatusChange) {
        return DoiRequestStatus.APPROVED.equals(requestedStatusChange);
    }

    private List<Publication> extractMostRecentVersionOfEachPublication(URI publisher) throws ApiGatewayException {

        return attempt(() -> queryByPublisher(publisher))
            .map(this::extractPublications)
            .map(this::filterNotPublishedPublications)
            .map(this::keepMostRecentPublications)
            .orElseThrow(this::handleDynamoDbException);
    }

    private List<Publication> filterNotPublishedPublications(List<Publication> list) {
        return list
            .stream()
            .filter(pub -> PublicationStatus.PUBLISHED.equals(pub.getStatus()))
            .collect(Collectors.toList());
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

    private <T> ApiGatewayException handleDynamoDbException(Failure<T> fail) {
        if (isAccessDeniedException(fail.getException())) {
            return new ForbiddenException();
        }
        return new DynamoDBException(ERROR_READING_FROM_TABLE, fail.getException());
    }

    private boolean isAccessDeniedException(Exception exception) {
        return exception instanceof AmazonDynamoDBException
            && exception.getMessage().contains(ACCESS_DENIED_ERROR_MESSAGE);
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
        Instant now = Instant.now(clockForTimestamps);
        return new DoiRequest.Builder()
            .withStatus(DoiRequestStatus.REQUESTED)
            .withCreatedDate(now)
            .withModifiedDate(now);
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

    private void putItem(Publication publication) throws ApiGatewayException {
        Item item = publicationToItem(publication);
        PutItemSpec putItemSpec = new PutItemSpec().withItem(item);
        attempt(() -> publicationsTable.putItem(putItemSpec))
            .orElseThrow(this::handleDynamoDbException);
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

package no.unit.nva.doi.requests.service.impl;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Index;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.RangeKeyCondition;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import no.unit.nva.doi.requests.exception.DynamoDBException;
import no.unit.nva.doi.requests.model.DoiRequestSummary;
import no.unit.nva.doi.requests.service.DoiRequestsService;
import no.unit.nva.model.DoiRequestStatus;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.utils.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamoDBDoiRequestsService implements DoiRequestsService {

    public static final String TABLE_NAME = "TABLE_NAME";
    public static final String INDEX_NAME = "INDEX_NAME";
    public static final String DOI_REQUEST_STATUS_DATE = "doiRequestStatusDate";
    public static final String PUBLISHER_ID = "publisherId";
    public static final String ERROR_READING_FROM_TABLE = "Error reading from table";

    private final Logger logger = LoggerFactory.getLogger(DynamoDBDoiRequestsService.class);
    private final Table table;
    private final Index index;
    private final ObjectMapper objectMapper;

    /**
     * Constructor for DynamoDBDoiRequestsService.
     *
     * @param objectMapper  objectMapper
     * @param table DynamoDB table
     * @param index DynamoDB index
     */
    public DynamoDBDoiRequestsService(ObjectMapper objectMapper, Table table, Index index) {
        this.objectMapper = objectMapper;
        this.table = table;
        this.index = index;
    }

    /**
     * Constructor for DynamoDBDoiRequestsService.
     *
     * @param client    AmazonDynamoDB client
     * @param objectMapper  objectMapper
     * @param environment   environment reader
     */
    public DynamoDBDoiRequestsService(AmazonDynamoDB client, ObjectMapper objectMapper, Environment environment) {
        String tableName = environment.readEnv(TABLE_NAME);
        String indexName = environment.readEnv(INDEX_NAME);
        DynamoDB dynamoDB = new DynamoDB(client);
        this.table = dynamoDB.getTable(tableName);
        this.index = table.getIndex(indexName);
        this.objectMapper = objectMapper;
    }

    @Override
    public List<DoiRequestSummary> findDoiRequestsByStatus(URI publisher, DoiRequestStatus status)
        throws ApiGatewayException {
        RangeKeyCondition rangeKeyCondition = new RangeKeyCondition(DOI_REQUEST_STATUS_DATE);
        rangeKeyCondition.beginsWith(status.toString());
        ItemCollection<QueryOutcome> outcome;
        try {
            outcome = index.query(PUBLISHER_ID, publisher.toString(), rangeKeyCondition);
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

    private List<DoiRequestSummary> parseJsonToDoiRequestSummaries(ItemCollection<QueryOutcome> items) {
        List<DoiRequestSummary> doiRequestSummaries = new ArrayList<>();
        items.forEach(item -> toDoiRequestSummary(item).ifPresent(doiRequestSummaries::add));
        return doiRequestSummaries;
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
}

/*
 *  Copyright (c) 2024, WSO2 LLC. (https://www.wso2.com).
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.carbon.connector.amazonsqs.operations.message;

import org.apache.axiom.om.OMElement;
import org.apache.commons.lang.StringUtils;
import org.apache.synapse.MessageContext;
import org.json.JSONArray;
import org.json.JSONObject;
import org.wso2.carbon.connector.amazonsqs.connection.SqsConnection;
import org.wso2.carbon.connector.amazonsqs.constants.Constants;
import org.wso2.carbon.connector.amazonsqs.exception.SqsInvalidConfigurationException;
import org.wso2.carbon.connector.amazonsqs.utils.Error;
import org.wso2.carbon.connector.amazonsqs.utils.Utils;
import org.wso2.carbon.connector.core.AbstractConnector;
import org.wso2.carbon.connector.core.ConnectException;
import org.wso2.carbon.connector.core.connection.ConnectionHandler;
import org.wso2.carbon.connector.core.util.ConnectorUtils;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchResponse;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchResultEntry;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Implements change message visibility batch operation.
 */
public class ChangeMessageVisibilityBatch extends AbstractConnector {

    @Override
    public void connect(MessageContext messageContext) throws ConnectException {
        try {
            ConnectionHandler handler = ConnectionHandler.getConnectionHandler();
            SqsConnection sqsConnection = (SqsConnection) handler
                    .getConnection(Constants.CONNECTOR_NAME, Utils.getConnectionName(messageContext));
            String queueUrl = Utils.createUrl(messageContext, sqsConnection);
            String messageRequestEntries = (String) ConnectorUtils.lookupTemplateParamater(messageContext,
                    Constants.MESSAGE_REQUEST_ENTRIES);
            ChangeMessageVisibilityBatchRequest.Builder changeMessageVisibilityBatchRequest =
                    ChangeMessageVisibilityBatchRequest.builder().queueUrl(queueUrl);
            List<ChangeMessageVisibilityBatchRequestEntry> entries = new ArrayList<>();
            JSONArray messageEntries = new JSONArray(messageRequestEntries);
            for (int i = 0; i < messageEntries.length(); i++) {
                JSONObject entryInJson = (JSONObject) messageEntries.get(i);
                Set keySet = entryInJson.keySet();
                ChangeMessageVisibilityBatchRequestEntry.Builder batchEntryBuilder =
                        ChangeMessageVisibilityBatchRequestEntry.builder();
                if (keySet.contains(Constants.ID)) {
                    batchEntryBuilder.id(entryInJson.get(Constants.ID).toString());
                } else {
                    String msg = "Missing required parameter: `id` in the message visibility batch entry.";
                    Utils.setErrorPropertiesToMessage(messageContext, Error.INVALID_CONFIGURATION, msg);
                    handleException(Constants.RUN_TIME_EXCEPTION_MSG, new SqsInvalidConfigurationException(msg),
                            messageContext);
                }

                if (keySet.contains(Constants.RECEIPT_HANDLE_CONFIG)) {
                    batchEntryBuilder.receiptHandle(entryInJson.get(Constants.RECEIPT_HANDLE_CONFIG).toString());
                } else {
                    String msg = "Missing required parameter: `receiptHandle` in the message visibility batch entry.";
                    Utils.setErrorPropertiesToMessage(messageContext, Error.INVALID_CONFIGURATION, msg);
                    handleException(Constants.RUN_TIME_EXCEPTION_MSG, new SqsInvalidConfigurationException(msg),
                            messageContext);
                }

                if (keySet.contains(Constants.VISIBILITY_TIME_OUT)) {
                    batchEntryBuilder.visibilityTimeout(Integer.valueOf(entryInJson.get(
                            Constants.VISIBILITY_TIME_OUT).toString()));
                }
                entries.add(batchEntryBuilder.build());
            }
            changeMessageVisibilityBatchRequest.entries(entries);
            String apiCallTimeout = (String) ConnectorUtils.lookupTemplateParamater(messageContext,
                    Constants.API_CALL_TIMEOUT);
            String apiCallAttemptTimeout = (String) ConnectorUtils.lookupTemplateParamater(messageContext,
                    Constants.API_CALL_ATTEMPT_TIMEOUT);
            if (StringUtils.isNotEmpty(apiCallTimeout) || StringUtils.isNotEmpty(apiCallAttemptTimeout)) {
                changeMessageVisibilityBatchRequest.overrideConfiguration(
                        Utils.getOverrideConfiguration(apiCallTimeout, apiCallAttemptTimeout).build());
            }
            ChangeMessageVisibilityBatchResponse response = sqsConnection.getSqsClient().changeMessageVisibilityBatch(
                    changeMessageVisibilityBatchRequest.build());

            OMElement resultElement = Utils.createOMElement("ChangeMessageVisibilityBatchResponse",
                    null);
            OMElement batchResultElement = Utils.createOMElement("ChangeMessageVisibilityBatchResponse",
                    null);
            for (ChangeMessageVisibilityBatchResultEntry entry: response.successful()) {
                OMElement batchResultEntryElement = Utils.createOMElement("SendMessageBatchResultEntry",
                        null);
                batchResultEntryElement.addChild(Utils.createOMElement(Constants.ID_KEY, entry.id()));
            }
            Utils.createBatchResultErrorEntryResponse(response.failed(), batchResultElement);
            resultElement.addChild(batchResultElement);
            Utils.createResponseMetaDataElement(response.responseMetadata(), messageContext, resultElement);
        } catch (SqsException e) {
            Utils.addErrorResponse(messageContext, e);
        } catch (SdkClientException e) {
            Utils.setErrorPropertiesToMessage(messageContext, Error.CLIENT_SDK_ERROR, e.getMessage());
            handleException(Constants.CLIENT_EXCEPTION_MSG, e, messageContext);
        } catch (SqsInvalidConfigurationException e) {
            Utils.setErrorPropertiesToMessage(messageContext, Error.INVALID_CONFIGURATION, e.getMessage());
            handleException(Constants.GENERAL_ERROR_MSG, e, messageContext);
        } catch (NumberFormatException e) {
            Utils.setErrorPropertiesToMessage(messageContext, Error.INVALID_CONFIGURATION, e.getMessage());
            handleException(Constants.NUMBER_FORMAT_ERROR_MSG, e, messageContext);
        } catch (Exception e) {
            Utils.setErrorPropertiesToMessage(messageContext, Error.GENERAL_ERROR, e.getMessage());
            handleException(Constants.GENERAL_ERROR_MSG + e.getMessage(), messageContext);
        }
    }
}

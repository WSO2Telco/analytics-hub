/*
 *  Copyright (c) 2016 WSO2.Telco Inc. (http://www.wso2telco.com) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2telco.analytics.hub.report.engine.internel.util;

import org.wso2.carbon.analytics.datasource.commons.Record;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.*;

public class CSVWriter {

    public static void writeCSV(List<Record> records, int bufSize, String filePath,
                                Map<String, String> dataColumns, List<String> columnHeads) throws IOException {

        File file = new File(filePath);
        file.getParentFile().mkdirs();
        FileWriter writer = new FileWriter(file, true);
        BufferedWriter bufferedWriter = new BufferedWriter(writer, bufSize);

        StringBuilder sb = new StringBuilder();

        for (String columnName : columnHeads) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(columnName);
        }

        sb.append(System.getProperty("line.separator"));
        bufferedWriter.write(sb.toString());

        for (Record record : records) {
            sb = new StringBuilder();

            for (String key : dataColumns.keySet()) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                if (dataColumns.get(key).equals("date")) {
                    Date date = new Date(Long.parseLong(record.getValues().get(key).toString()));
                    Format format = new SimpleDateFormat("yyyy MM dd HH:mm:ss");
                    sb.append(format.format(date));
                } else {
                    sb.append(clearSpecialCharacters(record.getValues().get(key)));
                }
            }

            sb.append(System.getProperty("line.separator"));

            bufferedWriter.write(sb.toString());
        }
        bufferedWriter.flush();
        bufferedWriter.close();
    }

    public static void writeTrafficCSV(List<Record> records, int bufSize, String filePath) throws IOException {

        File file = new File(filePath);
        file.getParentFile().mkdirs();
        FileWriter writer = new FileWriter(file, true);
        BufferedWriter bufferedWriter = new BufferedWriter(writer, bufSize);

        Map<String, Integer> apiCount = new HashMap<>();
        Integer count = 0;

        if (records.size() > 0) {
            for (Record record : records) {
                String key = record.getValues().get("api").toString();
                if (apiCount.containsKey(key)) {
                    count = apiCount.get(key) + Integer.parseInt(record.getValues().get("totalCount").toString());
                } else {
                    count = Integer.parseInt(record.getValues().get("totalCount").toString());
                }
                apiCount.put(key, count);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("API");
        sb.append(',');
        sb.append("Total Count");
        sb.append(System.getProperty("line.separator"));

        if (records.size() > 0) {
            for (String key : apiCount.keySet()) {
                sb.append(key);
                sb.append(',');
                sb.append(apiCount.get(key));
                sb.append(System.getProperty("line.separator"));
            }
            bufferedWriter.write(sb.toString());
        } else {
            bufferedWriter.write("No data available for this date range");
        }
        bufferedWriter.flush();
        bufferedWriter.close();

    }

    public static void writeBillingCSV(List<Record> records, int bufSize, String filePath, String table) throws
			IOException {
        File file = new File(filePath);
        file.getParentFile().mkdirs();
        FileWriter writer = new FileWriter(file, true);
        BufferedWriter bufferedWriter = new BufferedWriter(writer, bufSize);
        StringBuilder sb = new StringBuilder();

        if (table.equalsIgnoreCase("ORG_WSO2TELCO_ANALYTICS_HUB_STREAM_NORTHBOUND_REPORT_SUMMARY_PER_DAY")) {
            sb.append("API");
            sb.append(',');
            sb.append("SP Name");
            sb.append(',');
            sb.append("Application Name");
            sb.append(',');
            sb.append("Event Type");
            sb.append(',');
            sb.append("Purchase Category Code");
            sb.append(',');
            sb.append("Total amount");
            sb.append(',');
            sb.append("SP Revenue");
            sb.append(',');
            sb.append("Charge");
            sb.append(',');
            sb.append("Day");
            sb.append(System.getProperty("line.separator"));

            if (records.size() > 0) {
                for (Record record : records) {

                    String api = getValue(record.getValues().get("api"));
                    String spName = getValue(record.getValues().get("spName"));
                    String applicationName = getValue(record.getValues().get("applicationName"));
                    String eventType = getValue(record.getValues().get("eventType"));
                    String purchaseCategoryCode = getValue(record.getValues().get("category"));
                    String sum_totalAmount = getValue(record.getValues().get("sum_totalAmount"));
                    String spcommission = getValue(record.getValues().get("spCommission"));
                    String revShare_hub = getValue(record.getValues().get("revShare_hub"));
                    String _timestamp = new Date(new Long(record.getValues().get("eventTimeStamp").toString()))
							.toString();

                    sb.append(api);
                    sb.append(',');
                    sb.append(spName);
                    sb.append(',');
                    sb.append(applicationName);
                    sb.append(',');
                    sb.append(eventType);
                    sb.append(',');
                    sb.append(purchaseCategoryCode);
                    sb.append(',');
                    sb.append(sum_totalAmount);
                    sb.append(',');
                    sb.append(spcommission);
                    sb.append(',');
                    sb.append(revShare_hub);
                    sb.append(',');
                    sb.append(_timestamp);
                    sb.append(System.getProperty("line.separator"));
                }
            }
            bufferedWriter.write(sb.toString());
        } else if (table.equalsIgnoreCase("ORG_WSO2TELCO_ANALYTICS_HUB_STREAM_SOUTHBOUND_REPORT_SUMMARY_PER_DAY")) {
            sb.append("API");
            sb.append(',');
            sb.append("SP Name");
            sb.append(',');
            sb.append("Operator Name");
            sb.append(',');
            sb.append("Application Name");
            sb.append(',');
            sb.append("Event Type");
            sb.append(',');
            sb.append("Purchase Category Code");
            sb.append(',');
            sb.append("Total amount");
            sb.append(',');
            sb.append("MNO Share");
            sb.append(',');
            sb.append("SP Share");
            sb.append(',');
            sb.append("HUB Share");
            sb.append(',');
            sb.append("Day");
            sb.append(System.getProperty("line.separator"));

            if (records.size() > 0) {
                for (Record record : records) {

                    String api = getValue(record.getValues().get("api"));
                    String spName = getValue(record.getValues().get("spName"));
                    String operatorName = getValue(record.getValues().get("operatorName"));
                    String applicationName = getValue(record.getValues().get("applicationName"));
                    String eventType = getValue(record.getValues().get("eventType"));
                    String purchaseCategoryCode = getValue(record.getValues().get("category"));
                    String sum_totalAmount = getValue(record.getValues().get("sum_totalAmount"));
                    String mnoShare = record.getValues().get("revShare_opco") != null ? getValue(record.getValues()
							.get("revShare_opco")) : null;
                    String spShare = getValue(record.getValues().get("revShare_sp"));
                    String hubShare = getValue(record.getValues().get("revShare_hub"));
                    String _timestamp = new Date(new Long(record.getValues().get("eventTimeStamp").toString())).toString();

                    sb.append(api);
                    sb.append(',');
                    sb.append(spName);
                    sb.append(',');
                    sb.append(operatorName);
                    sb.append(',');
                    sb.append(applicationName);
                    sb.append(',');
                    sb.append(eventType);
                    sb.append(',');
                    sb.append(purchaseCategoryCode);
                    sb.append(',');
                    sb.append(sum_totalAmount);
                    sb.append(',');
                    sb.append(mnoShare);
                    sb.append(',');
                    sb.append(spShare);
                    sb.append(',');
                    sb.append(hubShare);
                    sb.append(',');
                    sb.append(_timestamp);
                    sb.append(System.getProperty("line.separator"));
                }
            }
            bufferedWriter.write(sb.toString());
        } else {
            bufferedWriter.write("No data available for this date range");
        }

        bufferedWriter.flush();
        bufferedWriter.close();

    }

    private static String getValue(Object val) {

        if (val != null) {
            return val.toString();
        } else {
            return "";
        }

    }

    /**
     * Handling special characters which creates errors in csv file.
     * New line and tab will be replaced with a space.
     */
    private static String clearSpecialCharacters(Object str) {
        if (str != null) {
            return str.toString().replace("\n", " ").replace("\r", " ").replace("\t", " ").replace(",", " ");
        }
        return null;
    }

}

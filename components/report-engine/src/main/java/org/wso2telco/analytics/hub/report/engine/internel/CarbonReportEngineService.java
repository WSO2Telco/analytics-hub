package org.wso2telco.analytics.hub.report.engine.internel;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.killbill.billing.client.model.Invoice;
import org.wso2.carbon.analytics.dataservice.commons.AnalyticsDataResponse;
import org.wso2.carbon.analytics.dataservice.commons.SearchResultEntry;
import org.wso2.carbon.analytics.dataservice.core.AnalyticsDataServiceUtils;
import org.wso2.carbon.analytics.datasource.commons.Record;
import org.wso2.carbon.analytics.datasource.commons.exception.AnalyticsException;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2telco.analytics.hub.report.engine.ReportEngineService;
import org.wso2telco.analytics.hub.report.engine.internel.ds.ReportEngineServiceHolder;
import org.wso2telco.analytics.hub.report.engine.internel.model.LoggedInUser;
import org.wso2telco.analytics.hub.report.engine.internel.util.CSVWriter;
import org.wso2telco.analytics.hub.report.engine.internel.util.PDFWriter;
import org.wso2telco.analytics.hub.report.engine.internel.util.ReportEngineServiceConstants;
import org.wso2telco.analytics.sparkUdf.exception.KillBillException;
import org.wso2telco.analytics.sparkUdf.service.AccountService;
import org.wso2telco.analytics.sparkUdf.service.InvoiceService;

import java.io.File;
import java.io.IOException;
import java.text.DateFormatSymbols;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public class CarbonReportEngineService implements ReportEngineService {

    private static ThreadPoolExecutor threadPoolExecutor;

    public CarbonReportEngineService() {
        threadPoolExecutor = new ThreadPoolExecutor(ReportEngineServiceConstants.SERVICE_MIN_THREAD_POOL_SIZE,
                ReportEngineServiceConstants.SERVICE_MAX_THREAD_POOL_SIZE,
                ReportEngineServiceConstants.DEFAULT_KEEP_ALIVE_TIME_IN_MILLIS,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(ReportEngineServiceConstants.SERVICE_EXECUTOR_JOB_QUEUE_SIZE));
    }

    public void generateReport(String tableName, String query, String reportName, int maxLength, String
            reportType, String columns, String fromDate, String toDate, String sp) {
        int tenantId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId(true);

        threadPoolExecutor.submit(new ReportEngineGenerator(tableName, query, maxLength, reportName, tenantId,
                reportType, columns, fromDate, toDate, sp));
    }

    public void generatePDFReport(String tableName, String query, String reportName, int maxLength, String
            reportType, String direction, String year, String month, boolean isServiceProvider, String loggedInUser,
                                  String billingInfo, String username) throws JSONException {
        int tenantId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId(true);

        threadPoolExecutor.submit(new PDFReportEngineGenerator(tableName, query, maxLength, reportName, tenantId,
                reportType, direction, year, month, isServiceProvider, loggedInUser, billingInfo, username));
    }
}

class ReportEngineGenerator implements Runnable {

    private static final Log log = LogFactory.getLog(ReportEngineGenerator.class);
    private String tableName;
    private String query;
    private int maxLength;
    private String reportName;
    private int tenantId;
    private String reportType;
    private String columns;
    private String fromDate;
    private String toDate;
    private String sp;

    public ReportEngineGenerator(String tableName, String query, int maxLength, String reportName, int tenantId,
                                 String reportType, String columns, String fromDate, String toDate, String sp) {
        this.tableName = tableName;
        this.query = query;
        this.maxLength = maxLength;
        this.reportName = reportName;
        this.tenantId = tenantId;
        this.reportType = reportType;
        this.columns = columns;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.sp = sp;
    }

    @Override
    public void run() {
        try {
            int searchCount = ReportEngineServiceHolder.getAnalyticsDataService()
                    .searchCount(tenantId, tableName, query);

            int writeBufferLength = 8192;

            if (reportType.equalsIgnoreCase("transaction")) {
                //Check weather search count is greater than the max file length and split files accordingly
                if (searchCount > maxLength) {
                    for (int i = 0; i < searchCount; ) {
                        int end = i + maxLength;
                        String filepath = reportName + "-" + i + "-" + end + ".csv";
                        generate(tableName, query, filepath, tenantId, i, maxLength, writeBufferLength);
                        i = end;
                    }
                } else {
                    String filepath = reportName + ".csv";
                    generate(tableName, query, filepath, tenantId, 0, searchCount, writeBufferLength);
                }
            } else if (reportType.equalsIgnoreCase("trafficCSV")) {
                //String filepath = reportName + ".csv";
                String filepath = reportName + ".csv";
                String tmpFilepath = reportName + ".wte";

                generate(tableName, query, tmpFilepath, tenantId, 0, searchCount, writeBufferLength);

                //if file is written successfully. rename file
                File tmpFile = new File(tmpFilepath);
                File newFile = new File(filepath);
                boolean isNameChanged = tmpFile.renameTo(newFile);

            } else if (reportType.equalsIgnoreCase("billingCSV")) {
                String filepath = reportName + ".csv";
                generate(tableName, query, filepath, tenantId, 0, searchCount, writeBufferLength);
            } else if (reportType.equalsIgnoreCase("billingErrorCSV")) {
                String filepath = reportName + ".csv";
                generate(tableName, query, filepath, tenantId, 0, searchCount, writeBufferLength);
            } else if (reportType.equalsIgnoreCase("responseTimeCSV")) {
                String filepath = reportName + ".csv";
                generate(tableName, query, filepath, tenantId, 0, searchCount, writeBufferLength);
            }

        } catch (SecurityException se) {
            log.error("Cannot Rename the .wte file");
        } catch (AnalyticsException e) {
            log.error("Data cannot be loaded for " + reportName + "report", e);
        } finally {
            //if exception occours delete tmp file
            try {
                String tmpFilepath = reportName + ".wte";
                File tmpFile = new File(tmpFilepath);
                if (tmpFile.exists()) {
                    tmpFile.delete();
                }
            } catch (SecurityException ex) {
                log.warn("temporarily generated traffic report deletion process failed");
            }
        }
    }

    public void generate(String tableName, String query, String filePath, int tenantId, int start,
                         int maxLength, int writeBufferLength)
            throws AnalyticsException {

        int dataCount = ReportEngineServiceHolder.getAnalyticsDataService()
                .searchCount(tenantId, tableName, query);
        List<Record> records = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        if (dataCount > 0) {
            List<SearchResultEntry> resultEntries = ReportEngineServiceHolder.getAnalyticsDataService()
                    .search(tenantId, tableName, query, start, maxLength);

            for (SearchResultEntry entry : resultEntries) {
                ids.add(entry.getId());
            }
            AnalyticsDataResponse resp = ReportEngineServiceHolder.getAnalyticsDataService()
                    .get(tenantId, tableName, 1, null, ids);

            records = AnalyticsDataServiceUtils
                    .listRecords(ReportEngineServiceHolder.getAnalyticsDataService(), resp);
            Collections.sort(records, new Comparator<Record>() {
                @Override
                public int compare(Record o1, Record o2) {
                    return Long.compare(o1.getTimestamp(), o2.getTimestamp());
                }
            });
        }


        Map<String, String> dataColumns = new LinkedHashMap<>();
        List<String> columnHeads = new ArrayList<>();
        if (StringUtils.isNotBlank(columns)) {
            try {

                JSONArray d = new JSONArray(columns);
                for (int i = 0; i < d.length(); i++) {
                    JSONObject jo = d.getJSONObject(i);
                    dataColumns.put(jo.get("column").toString(), jo.get("type").toString());
                    columnHeads.add(jo.get("label").toString());
                }
            } catch (JSONException e) {
                log.error("Invalid Json", e);
            }
        }

        try {
            if (reportType.equalsIgnoreCase("trafficCSV")) {
                CSVWriter.writeTrafficCSV(records, writeBufferLength, filePath);
            } else if (reportType.equalsIgnoreCase("billingErrorCSV")) {
                CSVWriter.writeErrorCSV(records, writeBufferLength, filePath, dataColumns, columnHeads);
            } else if (reportType.equalsIgnoreCase("responseTimeCSV")) {
                CSVWriter.writeResponseTImeCSV(records, writeBufferLength, filePath, dataColumns, columnHeads);
            } else if (reportType.equalsIgnoreCase("transaction")) {
                CSVWriter.writeTransactionCSV(records, writeBufferLength, filePath, dataColumns, columnHeads);
            } else {
                CSVWriter.writeCSV(records, writeBufferLength, filePath, dataColumns, columnHeads);
            }
        } catch (IOException e) {
            log.error("CSV file " + filePath + " cannot be created", e);
        }
    }

}


class PDFReportEngineGenerator implements Runnable {

    private static final Log log = LogFactory.getLog(ReportEngineGenerator.class);
    InvoiceService invoiceService = new InvoiceService();
    AccountService accountService = new AccountService();
    private String tableName;
    private String query;
    private int maxLength;
    private String reportName;
    private int tenantId;
    private String reportType;
    private String direction;
    private String year;
    private String month;
    private boolean isServiceProvider;
    private LoggedInUser loggedInUser;
    private JSONObject billingInfo;
    private String username;

    public PDFReportEngineGenerator(String tableName, String query, int maxLength, String reportName, int tenantId,
                                    String reportType, String direction, String year, String month, boolean isServiceProvider,
                                    String loggedInUserDetails, String billingInfo, String username) throws JSONException {

        this.tableName = tableName;
        this.query = query;
        this.maxLength = maxLength;
        this.reportName = reportName;
        this.tenantId = tenantId;
        this.reportType = reportType;
        this.direction = direction;
        this.year = year;
        this.month = month;
        this.isServiceProvider = isServiceProvider;
        this.loggedInUser = new Gson().fromJson(loggedInUserDetails, LoggedInUser.class);
        this.billingInfo = new JSONObject(billingInfo);
        this.username = username;
    }

    @Override
    public void run() {
        try {

            int searchCount = ReportEngineServiceHolder.getAnalyticsDataService()
                    .searchCount(tenantId, tableName, query);

            int writeBufferLength = 8192;

            if (reportType.equalsIgnoreCase("billingPDF")) {
                String filepath;
                if (isServiceProvider) {
                    filepath = "/repository/conf/spinvoice";
                } else if (loggedInUser.isOperatorAdmin()) {
                    filepath = "/repository/conf/sbinvoice_no_op";
                } else if ("sb".equalsIgnoreCase
                        (direction)) {
                    filepath = "/repository/conf/sbinvoice";
                } else {
                    filepath = "/repository/conf/nbinvoice";
                }
                generate(tableName, query, filepath, tenantId, 0, searchCount, year, month, username);
            }

        } catch (AnalyticsException e) {
            log.error("Data cannot be loaded for " + reportName + "report", e);
        }
    }

    public void generate(String tableName, String query, String filePath, int tenantId, int start,
                         int maxLength, String year, String month, String username)
            throws AnalyticsException {

        Record invoiceRecord = null;
        String accountId = getKillBillAccount(tenantId, username);
        Invoice invoiceForMonth = getInvoice(month, accountId);

        if (invoiceForMonth != null) {
            Map<String, Object> values = new HashMap<>();
            values.put("serviceProviderId", username);
            values.put("year", year);
            values.put("totalHbCommision", 0.0);
            values.put("totalCount", 0);
            values.put("operatorName", "");
            values.put("totalAmount", 0.0);
            values.put("month", month);
            values.put("serviceProvider", username);
            values.put("totalOpCommision", 0.0);
            values.put("api", "invoiceApi");
            values.put("totalSpCommision", 0.0);
            values.put("applicationId", 12);
            values.put("category", "");
            values.put("subcategory", "");
            values.put("totalTaxAmount", 0.0);
            values.put("_version", "1.0.0");
            values.put("operatorId", "1");
            values.put("operation", "invoice");
            values.put("applicationName", "appName");
            values.put("direction", direction);
            invoiceRecord = new Record(tenantId, tableName, values);
            invoiceRecord.setId(UUID.randomUUID().toString());

        }

        int dataCount = ReportEngineServiceHolder.getAnalyticsDataService()
                .searchCount(tenantId, tableName, query);
        List<Record> records = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        if (dataCount > 0) {
            List<SearchResultEntry> resultEntries = ReportEngineServiceHolder.getAnalyticsDataService()
                    .search(tenantId, tableName, query, start, maxLength);

            for (SearchResultEntry entry : resultEntries) {
                ids.add(entry.getId());
            }
            AnalyticsDataResponse resp = ReportEngineServiceHolder.getAnalyticsDataService()
                    .get(tenantId, tableName, 1, null, ids);

            records = AnalyticsDataServiceUtils
                    .listRecords(ReportEngineServiceHolder.getAnalyticsDataService(), resp);
            if(invoiceRecord != null){
                records.add(invoiceRecord);
            }
            Collections.sort(records, new Comparator<Record>() {
                @Override
                public int compare(Record o1, Record o2) {
                    return Long.compare(o1.getTimestamp(), o2.getTimestamp());
                }
            });
        }

        try {
            if (reportType.equalsIgnoreCase("billingPDF")) {
                HashMap param = new HashMap();
                param.put("R_INVNO", UUID.randomUUID().toString().substring(0, 6));
                param.put("R_YEAR", year);
                param.put("R_MONTH", month);
                param.put("R_SP", getHeaderText());
                param.put("R_ADDRESS", getAddress());
                param.put("R_PROMO_MSG", getPromoMessage());
                PDFWriter.generatePdf(reportName, filePath, records, param);
            }
        } catch (Exception e) {
            log.error("PDF file " + filePath + " cannot be created", e);
        }
    }

    private Invoice getInvoice(String month, String accountId) throws AnalyticsException {
        Invoice invoiceForMonth = null;
        try {
            List<Invoice> invoicesForAccount = invoiceService.getInvoicesForAccount(accountId);
            for (Invoice invoice : invoicesForAccount) {
                LocalDate targetDate = invoice.getTargetDate();
                int invoiceMonth = targetDate.getMonthOfYear();
                if (new DateFormatSymbols().getMonths()[invoiceMonth - 1].equals(month.trim())) {
                    invoiceForMonth = invoice;
                    break;
                }
            }
        } catch (KillBillException e) {
            throw new AnalyticsException("Error occurred while getting invoice from killbill", e);
        }
        return invoiceForMonth;
    }

    private String getKillBillAccount(int tenantId, String username) throws AnalyticsException {
        String killBillAccountQuery = "accountName:\"" + username + "\"";
        List<SearchResultEntry> killbillAccountsSearchResult = ReportEngineServiceHolder.getAnalyticsDataService()
                .search(tenantId, "ORG_WSO2TELCO_ANALYTICS_HUB_STREAM_KILLBILL_ACCOUNT", killBillAccountQuery, 0, 1);

        if (killbillAccountsSearchResult.isEmpty()) {
            throw new AnalyticsException("Could not find a kill bill account for " + username);
        }
        List<String> killBillSearchIds = killbillAccountsSearchResult.stream().map(SearchResultEntry::getId).collect(Collectors.toList());

        AnalyticsDataResponse killBillAccountResponse = ReportEngineServiceHolder.getAnalyticsDataService().get(tenantId,
                "ORG_WSO2TELCO_ANALYTICS_HUB_STREAM_KILLBILL_ACCOUNT", 1, null, killBillSearchIds);

        List<Record> killBillRecords = AnalyticsDataServiceUtils
                .listRecords(ReportEngineServiceHolder.getAnalyticsDataService(), killBillAccountResponse);

        return (String) killBillRecords.get(0).getValue("killBillAID");
    }

    private String getAddress() {
        String address = null;
        try {
            address = ((JSONObject) billingInfo.get("address")).getString(loggedInUser.getUsername()
                    .replace("@carbon.super", ""));
        } catch (JSONException e) {

            log.warn("couldn't find the address of " + loggedInUser.getUsername().replace("@carbon" +
                    ".super", "") + " from site.json");
        }
        return address;
    }

    private String getHeaderText() {
        String headerText = null;

        if (loggedInUser.isAdmin()) {
            try {
                headerText = ((JSONObject) billingInfo.get("hubName")).getString(loggedInUser.getUsername().replace("@carbon" +
                        ".super", ""));
            } catch (JSONException e) {
                log.warn("couldn't find the hubName from site.json for username " + loggedInUser
                        .getUsername().replace("@carbon" +
                                ".super", ""));
            }
        } else if (loggedInUser.isOperatorAdmin()) {
            headerText = loggedInUser.getOperatorNameInProfile();
        } else if (loggedInUser.isServiceProvider()) {
            headerText = loggedInUser.getUsername().replace("@carbon.super", "");
        }
        return headerText;
    }

    private String getPromoMessage() {
        String promoMessage = null;

        try {
            if (loggedInUser.isAdmin()) {
                promoMessage = ((JSONObject) billingInfo.get("promoMessage")).getString("hubAdmin");
            } else if (loggedInUser.isOperatorAdmin()) {
                promoMessage = ((JSONObject) billingInfo.get("promoMessage")).getString("operator");
            } else if (loggedInUser.isServiceProvider()) {
                promoMessage = ((JSONObject) billingInfo.get("promoMessage")).getString("serviceProvider");
            }
        } catch (JSONException e) {
            log.warn("couldn't find the promoMessage from site.json for username " + loggedInUser
                    .getUsername().replace("@carbon" +
                            ".super", ""));
        }
        return promoMessage;
    }

}

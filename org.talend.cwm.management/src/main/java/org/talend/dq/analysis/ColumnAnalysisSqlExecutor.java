// ============================================================================
//
// Copyright (C) 2006-2007 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.dq.analysis;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.eclipse.emf.common.util.EList;
import org.talend.cwm.helper.ColumnHelper;
import org.talend.cwm.helper.SwitchHelpers;
import org.talend.cwm.management.api.DbmsLanguage;
import org.talend.cwm.management.api.SoftwareSystemManager;
import org.talend.cwm.relational.TdColumn;
import org.talend.cwm.softwaredeployment.TdDataProvider;
import org.talend.cwm.softwaredeployment.TdSoftwareSystem;
import org.talend.dataquality.analysis.Analysis;
import org.talend.dataquality.analysis.AnalysisResult;
import org.talend.dataquality.domain.Domain;
import org.talend.dataquality.domain.RangeRestriction;
import org.talend.dataquality.helpers.DomainHelper;
import org.talend.dataquality.helpers.IndicatorDocumentationHandler;
import org.talend.dataquality.indicators.DateGrain;
import org.talend.dataquality.indicators.Indicator;
import org.talend.dataquality.indicators.IndicatorParameters;
import org.talend.dataquality.indicators.IndicatorsPackage;
import org.talend.dataquality.indicators.TextParameters;
import org.talend.dataquality.indicators.definition.IndicatorDefinition;
import org.talend.sqltools.ZQueryHelper;
import org.talend.utils.sugars.TypedReturnCode;
import orgomg.cwm.foundation.softwaredeployment.DataManager;
import orgomg.cwm.objectmodel.core.CoreFactory;
import orgomg.cwm.objectmodel.core.Expression;
import orgomg.cwm.objectmodel.core.ModelElement;
import orgomg.cwm.objectmodel.core.Package;

import Zql.ParseException;
import Zql.ZExp;
import Zql.ZQuery;
import Zql.ZqlParser;

/**
 * DOC scorreia class global comment. Detailled comment
 */
public class ColumnAnalysisSqlExecutor extends ColumnAnalysisExecutor {

    /**
     * TODO scorreia this constant must be replaced by a default preference and the possibility to the user to change it
     * for each indicator.
     */
    private static final int TOP_N = 20;

    private static Logger log = Logger.getLogger(ColumnAnalysisSqlExecutor.class);

    private static final String DEFAULT_QUOTE_STRING = "";

    private DbmsLanguage dbmsLanguage;

    private String dbQuote;

    private Analysis cachedAnalysis;

    /*
     * (non-Javadoc)
     * 
     * @see org.talend.dq.analysis.AnalysisExecutor#createSqlStatement(org.talend.dataquality.analysis.Analysis)
     */
    @Override
    protected String createSqlStatement(Analysis analysis) {
        this.cachedAnalysis = analysis;
        AnalysisResult results = analysis.getResults();
        assert results != null;

        try {
            // get data filter
            ZExp dataFilterExpression = null;
            ColumnAnalysisHandler handler = new ColumnAnalysisHandler();
            handler.setAnalysis(analysis);
            String stringDataFilter = handler.getStringDataFilter();
            if (StringUtils.isNotBlank(stringDataFilter)) {
                ZqlParser filterParser = new ZqlParser();
                filterParser.initParser(new ByteArrayInputStream(stringDataFilter.getBytes()));
                dataFilterExpression = filterParser.readExpression();
            }
            // create one sql statement for each indicator
            EList<Indicator> indicators = results.getIndicators();
            for (Indicator indicator : indicators) {
                if (!createSqlQuery(dataFilterExpression, indicator)) {
                    // execute java indicator
                    continue;
                }
            }
        } catch (ParseException e) {
            log.error(e, e);
            return null;
        }

        return "";
    }

    /**
     * DOC scorreia Comment method "createSqlQuery".
     * 
     * @param dataFilterExpression
     * 
     * @param analysis
     * 
     * @param indicator
     * @throws ParseException
     */
    private boolean createSqlQuery(ZExp dataFilterExpression, Indicator indicator) throws ParseException {
        ModelElement analyzedElement = indicator.getAnalyzedElement();
        if (analyzedElement == null) {
            log.error("Analyzed element for indicator "
                    + IndicatorDocumentationHandler.getName(indicator.eClass().getClassifierID()));
            return false;
        }
        TdColumn tdColumn = SwitchHelpers.COLUMN_SWITCH.doSwitch(indicator.getAnalyzedElement());
        if (tdColumn == null) {
            log.error("Analyzed element is not a column for indicator "
                    + IndicatorDocumentationHandler.getName(indicator.eClass().getClassifierID()));
            return false;
        }
        // --- get the schema owner
        String colName = tdColumn.getName();
        if (!belongToSameSchemata(tdColumn)) {
            StringBuffer buf = new StringBuffer();
            for (orgomg.cwm.objectmodel.core.Package schema : schemata.values()) {
                buf.append(schema.getName() + " ");
            }
            log.error("Column " + colName + " does not belong to an existing schema [" + buf.toString().trim() + "]");
            return false;
        }

        // get correct language for current database
        String language = dbms().getDbmsName();
        Expression sqlGenericExpression = null; // SqlIndicatorHandler.getSqlCwmExpression(indicator, language);

        // --- create select statement
        // get indicator's sql columnS (generate the real SQL statement from its definition)

        IndicatorDefinition indicatorDefinition = indicator.getIndicatorDefinition();
        if (indicatorDefinition == null) {
            log.error("No indicator definition found for indicator "
                    + IndicatorDocumentationHandler.getName(indicator.eClass().getClassifierID()));
            return false;
        }
        sqlGenericExpression = getSqlExpression(indicatorDefinition, language);
        if (sqlGenericExpression == null) {
            // try with default language (ANSI SQL)
            log.warn("The indicator definition has not been found for the database type " + language + " for the indicator"
                    + indicatorDefinition.getName());
            if (log.isInfoEnabled()) {
                log.info("Trying to compute the indicator with the default language " + dbms().getDefaultLanguage());
            }
            sqlGenericExpression = getSqlExpression(indicatorDefinition, dbms().getDefaultLanguage());
        }

        if (sqlGenericExpression == null || sqlGenericExpression.getBody() == null) {
            log.error("No SQL expression found for indicator "
                    + IndicatorDocumentationHandler.getName(indicator.eClass().getClassifierID()));
            return false;
        }

        // --- get indicator parameters and convert them into sql expression
        List<String> whereExpression = new ArrayList<String>();
        List<String> rangeStrings = null;
        DateGrain dateAggregationType = null;
        IndicatorParameters parameters = indicator.getParameters();
        if (parameters != null) {
            // handle bins
            Domain bins = parameters.getBins();
            if (bins != null) {
                rangeStrings = getBinsAsGenericString(bins.getRanges());
            }
            dateAggregationType = parameters.getDateAggregationType();
            // TODO handle data grain

            TextParameters textParameter = parameters.getTextParameter();
            colName = quote(colName);
            if (textParameter != null) {
                if (textParameter.isIgnoreCase()) {
                    colName = dbms().toUpperCase(colName);
                }
                if (!textParameter.isUseBlank()) {
                    whereExpression.add(dbms().isNotBlank(colName));
                }
                if (textParameter.isUseNulls()) {
                    colName = dbms().replaceNullsWithString(colName, "''");
                }
            }
        }

        String table = ColumnHelper.getColumnSetFullName(tdColumn);

        // --- normalize table name
        String catalogName = getCatalogName(tdColumn);
        table = dbms().toQualifiedName(catalogName, null, table);

        // ### evaluate SQL Statement depending on indicators ###
        String completedSqlString = null;

        // --- handle case when indicator is a quantile
        if (indicator.eClass().equals(IndicatorsPackage.eINSTANCE.getMedianIndicator())
                || indicator.eClass().equals(IndicatorsPackage.eINSTANCE.getLowerQuartileIndicator())
                || indicator.eClass().equals(IndicatorsPackage.eINSTANCE.getUpperQuartileIndicator())) {
            completedSqlString = getCompletedStringForQuantiles(indicator, sqlGenericExpression, colName, table,
                    dataFilterExpression);
            completedSqlString = addWhereToSqlStringStatement(dataFilterExpression, whereExpression, completedSqlString);
        } else

        // --- handle case when frequency indicator
        if (indicator.eClass().equals(IndicatorsPackage.eINSTANCE.getFrequencyIndicator())) {
            // with ranges (frequencies of numerical intervals)
            if (rangeStrings != null) {
                completedSqlString = getUnionCompletedString(indicator, sqlGenericExpression, colName, table,
                        dataFilterExpression, rangeStrings);
            } else if (dateAggregationType != null) { // frequencies with date aggregation
                // TODO scorreia handle date frequencies
                completedSqlString = getDateAggregatedCompletedString(sqlGenericExpression, colName, table, dateAggregationType);
                completedSqlString = getTopN(completedSqlString, TOP_N);
                completedSqlString = addWhereToSqlStringStatement(dataFilterExpression, whereExpression, completedSqlString);
            } else { // usual nominal frequencies
                completedSqlString = replaceVariables(sqlGenericExpression.getBody(), colName, quote(table)) + dbms().eos();
                completedSqlString = getTopN(completedSqlString, TOP_N);
                completedSqlString = addWhereToSqlStringStatement(dataFilterExpression, whereExpression, completedSqlString);
            }
        } else

        // --- handle case of unique and duplicate count
        if (indicator.eClass().equals(IndicatorsPackage.eINSTANCE.getUniqueCountIndicator())
                || indicator.eClass().equals(IndicatorsPackage.eINSTANCE.getDuplicateCountIndicator())) {
            completedSqlString = replaceVariables(sqlGenericExpression.getBody(), colName, quote(table)) + dbms().eos();
            completedSqlString = addWhereToSqlStringStatement(dataFilterExpression, whereExpression, completedSqlString);
            completedSqlString = dbms().countRowInSubquery(completedSqlString, "myquery");
        } else {

            // --- default case
            completedSqlString = replaceVariables(sqlGenericExpression.getBody(), colName, quote(table)) + dbms().eos();
            completedSqlString = addWhereToSqlStringStatement(dataFilterExpression, whereExpression, completedSqlString);
        }

        if (log.isDebugEnabled()) {
            log.debug("Completed SQL expression for language " + language + ": " + completedSqlString);
        }

        // TODO scorreia completedSqlString should be the final query
        String finalQuery = completedSqlString;

        Expression instantiateSqlExpression = instantiateSqlExpression(language, finalQuery);
        indicator.setInstantiatedExpression(instantiateSqlExpression);
        return true;
    }

    /**
     * DOC scorreia Comment method "getDateAggregatedCompletedString".
     * 
     * @param indicator
     * @param sqlExpression
     * @param colName
     * @param table
     * @param dataFilterExpression
     * @param dateAggregationType
     * @return
     */
    private String getDateAggregatedCompletedString(Expression sqlExpression, String colName, String table,
            DateGrain dateAggregationType) {
        int nbExtractedColumns = 0;
        String result = "";
        switch (dateAggregationType) {
        case DAY:
            result += dbms().extractDay(colName);
            nbExtractedColumns++;
        case WEEK:
            result = dbms().extractWeek(colName) + result;
            nbExtractedColumns++;
        case MONTH:
            result = dbms().extractMonth(colName) + result;
            nbExtractedColumns++;
        case QUARTER:
            result = dbms().extractQuarter(colName) + result;
            nbExtractedColumns++;
        case SEMESTER:
            // FIXME scorreia semester not handled
        case YEAR:
            result = dbms().extractYear(colName) + result;
            nbExtractedColumns++;
            break;
        case NONE:
            result = colName;
            nbExtractedColumns++;
            break;
        default:
            break;
        }

        String sql = replaceVariables(sqlExpression.getBody(), result, table);
        return sql;
    }

    /**
     * DOC scorreia Comment method "getTopN".
     * 
     * @param completedSqlString
     * @param i
     * @return
     */
    private String getTopN(String completedSqlString, int i) {
        return completedSqlString + " LIMIT " + i;
    }

    /**
     * DOC scorreia Comment method "getFinalSqlStringStatement".
     * 
     * @param dataFilterExpression
     * @param whereExpression
     * @param completedSqlString
     * @return
     * @throws ParseException
     */
    private String addWhereToSqlStringStatement(ZExp dataFilterExpression, List<String> whereExpression, String completedSqlString)
            throws ParseException {
        TypedReturnCode<ZQuery> trc = dbms().parseQuery(completedSqlString);
        ZQuery query = trc.getObject();
        if (dataFilterExpression != null) {
            query.addWhere(dataFilterExpression);
        }

        Vector<ZExp> whereVector = ZQueryHelper.createWhereVector(whereExpression.toArray(new String[whereExpression.size()]));
        for (ZExp exp : whereVector) {
            query.addWhere(exp);
        }

        // set the instantiated sql expression into the indicator.
        String finalQuery = dbms().finalizeQuery(query);
        return finalQuery;
    }

    /**
     * DOC scorreia Comment method "getUnionCompletedString".
     * 
     * @param indicator
     * @param sqlExpression
     * @param colName
     * @param table
     * @param dataFilterExpression
     * @param rangeStrings
     * @return
     * @throws ParseException
     */
    private String getUnionCompletedString(Indicator indicator, Expression sqlExpression, String colName, String table,
            ZExp dataFilterExpression, List<String> rangeStrings) throws ParseException {
        StringBuffer buf = new StringBuffer();
        final int last = rangeStrings.size();
        // remove unused LIMIT
        String sqlGenericExpression = sqlExpression.getBody();
        for (int i = 0; i < last; i++) {

            String singleSelect = getCompletedSingleSelect(indicator, sqlGenericExpression, colName, table, dataFilterExpression,
                    rangeStrings.get(i));
            buf.append('(');
            buf.append(singleSelect);
            buf.append(')');
            if (i != last - 1) {
                buf.append(dbms().unionAll());
            }
        }
        return buf.toString();
    }

    /**
     * DOC scorreia Comment method "getCompletedSingleSelect".
     * 
     * @param indicator
     * @param sqlExpression
     * @param colName
     * @param table
     * @param dataFilterExpression
     * @param range
     * @return
     * @throws ParseException
     */
    private String getCompletedSingleSelect(Indicator indicator, String sqlGenericExpression, String colName, String table,
            ZExp dataFilterExpression, String range) throws ParseException {
        String completedRange = replaceVariables(range, colName, table);
        String rangeColumn = "'" + completedRange + "'";
        String completedSqlString = replaceVariables(sqlGenericExpression, rangeColumn, table);
        List<String> listOfWheres = Arrays.asList(completedRange);
        return addWhereToSqlStringStatement(dataFilterExpression, listOfWheres, completedSqlString);
    }

    /**
     * DOC scorreia Comment method "getBinsAsString".
     * 
     * @param ranges
     * @return
     */
    private List<String> getBinsAsGenericString(EList<RangeRestriction> ranges) {
        List<String> bins = new ArrayList<String>();
        for (RangeRestriction rangeRestriction : ranges) {
            String bin = "{0} >= " + DomainHelper.getMinValue(rangeRestriction) + " AND {0} < "
                    + DomainHelper.getMaxValue(rangeRestriction);
            bins.add(bin);
        }
        return bins;
    }

    /**
     * DOC scorreia Comment method "getSqlExpression".
     * 
     * @param indicatorDefinition
     * @param language
     * @param sqlExpression
     * @return
     */
    private Expression getSqlExpression(IndicatorDefinition indicatorDefinition, String language) {
        EList<Expression> sqlGenericExpression = indicatorDefinition.getSqlGenericExpression();
        for (Expression sqlGenExpr : sqlGenericExpression) {
            if (StringUtils.equalsIgnoreCase(language, sqlGenExpr.getLanguage())) {
                return sqlGenExpr; // language found
            }
        }
        return null;
    }

    /**
     * DOC scorreia Comment method "getCompletedString".
     * 
     * @param indicator
     * @param sqlExpression
     * @param colName
     * @param table
     * @param dataFilterExpression
     */
    private String getCompletedStringForQuantiles(Indicator indicator, Expression sqlExpression, String colName, String table,
            ZExp dataFilterExpression) {
        // first, count nb lines
        String catalog = getCatalogName(indicator.getAnalyzedElement());
        long count = getCount(cachedAnalysis, colName, quote(table), catalog, dataFilterExpression);
        if (count == -1) {
            log.error("Cannot count number of lines in table " + table);
            return null;
        }

        Long midleCount = getLimitFirstArg(indicator, count);
        Integer nbRow = getNbReturnedRows(indicator, count);
        return MessageFormat.format(sqlExpression.getBody(), quote(colName), quote(table), String.valueOf(midleCount), String
                .valueOf(nbRow))
                + dbms().eos();
    }

    /**
     * DOC scorreia Comment method "getNbReturnedRows".
     * 
     * @param indicator
     * @param count
     * @return
     */
    private Integer getNbReturnedRows(Indicator indicator, long count) {
        if (indicator.eClass().equals(IndicatorsPackage.eINSTANCE.getMedianIndicator())) {
            boolean isEven = count % 2 == 0;
            return (isEven) ? 2 : 1;
        }
        return 1;
    }

    /**
     * DOC scorreia Comment method "getLimitFirstArg".
     * 
     * @param indicator
     * @param count
     * @return
     */
    private Long getLimitFirstArg(Indicator indicator, long count) {
        if (indicator.eClass().equals(IndicatorsPackage.eINSTANCE.getMedianIndicator())) {
            boolean isEven = count % 2 == 0;
            return isEven ? count / 2 - 1 : (count - 1) / 2;
        } else if (indicator.eClass().equals(IndicatorsPackage.eINSTANCE.getLowerQuartileIndicator())) {
            return count / 4 - 1;
        } else if (indicator.eClass().equals(IndicatorsPackage.eINSTANCE.getUpperQuartileIndicator())) {
            return (3 * count) / 4 - 1;
        }
        return null;
    }

    private Long getCount(Analysis analysis, String colName, String table, String catalog, ZExp dataFilterExpression) {
        try {
            return getCountLow(analysis, colName, table, catalog, dataFilterExpression);
        } catch (SQLException e) {
            log.error(e, e);
            return -1L;
        }
    }

    /**
     * DOC scorreia Comment method "getCount".
     * 
     * @param cachedAnalysis2
     * @param colName
     * @param quote
     * @param dataFilterExpression
     * @param catalogName
     * @return
     * @throws SQLException
     */
    private Long getCountLow(Analysis analysis, String colName, String table, String catalogName, ZExp dataFilterExpression)
            throws SQLException {
        TypedReturnCode<Connection> trc = this.getConnection(analysis);
        if (!trc.isOk()) {
            log.error("Cannot execute Analysis " + analysis.getName() + ". Error: " + trc.getMessage());
            return -1L;
        }
        Connection connection = trc.getObject();
        String whereExp = (dataFilterExpression == null || dataFilterExpression.toString().trim().length() == 0) ? "" : " where "
                + dataFilterExpression.toString();
        String queryStmt = "SELECT COUNT(" + colName + ") from " + table + whereExp + dbms().eos();

        List<Object[]> myResultSet = executeQuery(catalogName, connection, queryStmt);

        if (myResultSet.isEmpty() || myResultSet.size() > 1) {
            log.error("Too many result obtained for a simple count: " + myResultSet);
            return -1L;
        }
        return (Long) myResultSet.get(0)[0];
    }

    /**
     * DOC scorreia Comment method "getLanguage".
     * 
     * @return
     */
    private DbmsLanguage createDbmsLanguage() {
        DataManager connection = this.cachedAnalysis.getContext().getConnection();
        if (connection == null) {
            return new DbmsLanguage();
        }
        TdDataProvider dataprovider = SwitchHelpers.TDDATAPROVIDER_SWITCH.doSwitch(connection);
        if (dataprovider == null) {
            return new DbmsLanguage();
        }

        TdSoftwareSystem softwareSystem = SoftwareSystemManager.getInstance().getSoftwareSystem(dataprovider);
        if (softwareSystem == null) {
            return new DbmsLanguage();
        }
        return new DbmsLanguage(softwareSystem.getSubtype());
    }

    /**
     * Method "dbms".
     * 
     * @return the DBMS language (not null)
     */
    private DbmsLanguage dbms() {
        if (this.dbmsLanguage == null) {
            this.dbmsLanguage = createDbmsLanguage();
        }
        return this.dbmsLanguage;
    }

    // private static final String DEFAULT_SQL = "SQL";

    private Expression instantiateSqlExpression(String language, String body) {
        Expression expression = CoreFactory.eINSTANCE.createExpression();
        expression.setLanguage(language);
        expression.setBody(body);
        return expression;
    }

    /**
     * Method "replaceVariables".
     * 
     * @param sqlGenericString a string with 2 parameters {0} and {1}
     * @param column the string that replaces the {0} parameter
     * @param table the string that replaces the {1} parameter
     * @return the string with the given parameters
     */
    private String replaceVariables(String sqlGenericString, String column, String table) {
        Object[] arguments = { column, table };
        String toFormat = surroundSingleQuotes(sqlGenericString);

        return MessageFormat.format(toFormat, arguments);
    }

    /**
     * Method "surroundSingleQuotes".
     * 
     * see http://java.sun.com/j2se/1.4.2/docs/api/java/text/MessageFormat.html
     * 
     * @param sqlGenericString
     * @return
     */
    private String surroundSingleQuotes(String sqlGenericString) {
        return sqlGenericString.replaceAll("'", "''");
    }

    /**
     * DOC scorreia Comment method "getUpperCase".
     * 
     * @param language
     * @param colName
     * @return
     */

    /**
     * Method "quote".
     * 
     * @param input
     * @return the given string between quotes
     */
    private String quote(String input) {
        if (true) { // FIXME scorreia ZQL does not handle well quote strings
            return input;
        }
        return getDbQuoteString(this.cachedAnalysis) + input + getDbQuoteString(this.cachedAnalysis);
    }

    /**
     * Method "getDbQuoteString".
     * 
     * @param analysis
     * @return the database identifier quote string
     */
    private String getDbQuoteString(Analysis analysis) {
        if (dbQuote != null) {
            return dbQuote;
        }
        TypedReturnCode<Connection> trc = this.getConnection(analysis);
        if (!trc.isOk()) {
            log.error("Cannot execute Analysis " + analysis.getName() + ". Error: " + trc.getMessage());
            return DEFAULT_QUOTE_STRING;
        }
        try {
            dbQuote = DEFAULT_QUOTE_STRING;
            dbQuote = trc.getObject().getMetaData().getIdentifierQuoteString();
            trc.getObject().close();
            return dbQuote;
        } catch (SQLException e) {
            log.warn("Could not get identifier quote string from database for analysis " + analysis.getName());
            return DEFAULT_QUOTE_STRING;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.talend.dq.analysis.AnalysisExecutor#runAnalysis(org.talend.dataquality.analysis.Analysis,
     * java.lang.String)
     */
    @Override
    protected boolean runAnalysis(Analysis analysis, String sqlStatement) {
        boolean ok = true;
        TypedReturnCode<Connection> trc = this.getConnection(analysis);
        if (!trc.isOk()) {
            log.error("Cannot execute Analysis " + analysis.getName() + ". Error: " + trc.getMessage());
            return false;
        }

        try {
            Connection connection = trc.getObject();
            // execute the sql statement for each indicator
            EList<Indicator> indicators = analysis.getResults().getIndicators();
            for (Indicator indicator : indicators) {
                // set the connection's catalog
                String catalogName = getCatalogName(indicator.getAnalyzedElement());
                if (catalogName != null) { // check whether null argument can be given
                    connection.setCatalog(quote(catalogName));
                }

                Expression query = indicator.getInstantiatedExpressions(dbms().getDbmsName());
                if (query == null) {
                    // try to get a default sql expression
                    query = indicator.getInstantiatedExpressions(dbms().getDefaultLanguage());
                }
                if (query == null || !executeQuery(indicator, connection, query.getBody())) {
                    ok = false;
                }
            }
            // get the results

            // store the results in each indicator

            connection.close();
        } catch (SQLException e) {
            log.error(e, e);
        }
        return ok;
    }

    /**
     * DOC scorreia Comment method "getCatalogName".
     * 
     * @param analyzedElement
     * @return
     */
    private String getCatalogName(ModelElement analyzedElement) {
        Package schema = super.schemata.get(analyzedElement);
        if (schema == null) {
            log.error("No schema found for column " + analyzedElement.getName());
            return null;
        }
        // else
        return schema.getName();
        // if (tdColumn == null) {
        // log.error("Analyzed element is not a column: " +analyzedElement.getName());
        // return null;
        // }
        // this.belongToSameSchemata(tdColumn, schemata)
        // return null;
    }

    /**
     * DOC scorreia Comment method "executeQuery".
     * 
     * @param indicator
     * 
     * @param connection
     * 
     * @param queryStmt
     * @return
     * @throws SQLException
     */
    private boolean executeQuery(Indicator indicator, Connection connection, String queryStmt) throws SQLException {
        String cat = getCatalogName(indicator.getAnalyzedElement());
        List<Object[]> myResultSet = executeQuery(cat, connection, queryStmt);

        // give result to indicator so that it handles the results
        return indicator.storeSqlResults(myResultSet);
    }

    /**
     * DOC scorreia Comment method "executeQuery".
     * 
     * @param catalogName (can be null)
     * @param connection
     * @param queryStmt
     * @return
     * @throws SQLException
     */
    private List<Object[]> executeQuery(String catalogName, Connection connection, String queryStmt) throws SQLException {

        if (catalogName != null) { // check whether null argument can be given
            connection.setCatalog(quote(catalogName));
        }
        // create query statement
        // Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY,
        // ResultSet.CLOSE_CURSORS_AT_COMMIT);
        Statement statement = connection.createStatement();
        // statement.setFetchSize(fetchSize);
        if (log.isDebugEnabled()) {
            log.debug("Excuting query: " + queryStmt);
        }
        statement.execute(queryStmt);

        // get the results
        ResultSet resultSet = statement.getResultSet();
        if (resultSet == null) {
            String mess = "No result set for this statement: " + queryStmt;
            log.warn(mess);
            return null;
        }
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        List<Object[]> myResultSet = new ArrayList<Object[]>();
        while (resultSet.next()) {
            Object[] result = new Object[columnCount];
            for (int i = 0; i < columnCount; i++) {
                result[i] = resultSet.getObject(i + 1);
            }
            myResultSet.add(result);
        }
        resultSet.close();

        return myResultSet;
    }

}

package com.collective.celos.ci.testing.fixtures.create;

import com.collective.celos.DatabaseName;
import com.collective.celos.Util;
import com.collective.celos.ci.mode.test.TestRun;
import com.collective.celos.ci.testing.structure.fixobject.FixTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by akonopko on 22.01.15.
 */
public class OutputFixTableFromHiveCreator implements FixObjectCreator<FixTable> {

    private final static String READ_TABLE_DATA = "SELECT * FROM %s.%s";
    public static final String DESCRIPTION = "Hive table %s.%s";

    private final DatabaseName databaseName;
    private final String tableName;

    public OutputFixTableFromHiveCreator(DatabaseName databaseName, String tableName) {
        this.databaseName = databaseName;
        this.tableName = tableName;
    }

    @Override
    public FixTable create(TestRun testRun) throws Exception {

        Connection connection = DriverManager.getConnection(testRun.getCiContext().getTarget().getHiveJdbc().toString());
        try {
            return createFixTable(testRun, connection);
        } finally {
            connection.close();
        }
    }

    FixTable createFixTable(TestRun testRun, Connection connection) throws SQLException {
        String augmentedDbData = databaseName.getMockedName(testRun.getTestUUID());
        String query = String.format(READ_TABLE_DATA, augmentedDbData, tableName);
        ResultSet rs = connection.createStatement().executeQuery(query);

        List<String> columnNames = Lists.newArrayList();
        for (int i=0; i < rs.getMetaData().getColumnCount(); i++) {
            columnNames.add(rs.getMetaData().getColumnName(i + 1));
        }

        List<FixTable.FixRow> fixRows = Lists.newArrayList();
        while (rs.next()) {
            Map<String, String> rowData = new HashMap<>();
            for (String colName : columnNames) {
                rowData.put(colName, rs.getString(colName));
            }
            fixRows.add(new FixTable.FixRow(rowData));
        }

        return new FixTable(columnNames, fixRows);
    }

    @Override
    public String getDescription(TestRun testRun) {
        return String.format(DESCRIPTION, databaseName.getMockedName(testRun.getTestUUID()), tableName);
    }
}
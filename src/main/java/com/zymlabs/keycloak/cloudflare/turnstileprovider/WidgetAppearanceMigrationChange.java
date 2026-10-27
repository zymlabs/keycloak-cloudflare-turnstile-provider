package com.zymlabs.keycloak.cloudflare.turnstileprovider;

import com.zymlabs.keycloak.cloudflare.turnstileprovider.WidgetAppearanceMigration.Plan;
import com.zymlabs.keycloak.cloudflare.turnstileprovider.WidgetAppearanceMigration.Row;
import liquibase.exception.CustomChangeException;
import liquibase.statement.core.DeleteStatement;
import liquibase.statement.core.UpdateStatement;
import liquibase.structure.core.Table;
import org.keycloak.connections.jpa.updater.liquibase.custom.CustomKeycloakTask;
import org.keycloak.services.ui.extend.UiTabProvider;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The changeset that renames the widget setting stored before this version ({@code widgetMode}) to
 * {@code widgetAppearance}, in Turnstile step configs and realm settings. It runs once per database,
 * the first time Keycloak starts with this version. Values are compared in Java: they are stored in
 * CLOB columns, which not every database compares in SQL.
 */
public class WidgetAppearanceMigrationChange extends CustomKeycloakTask {

    @Override
    protected void generateStatementsImpl() throws CustomChangeException {
        try {
            int steps = renameStepSettings();
            int realms = renameRealmSettings();
            confirmationMessage.append(String.format("renamed in %d step configs and %d realm settings", steps, realms));
        } catch (SQLException e) {
            throw new CustomChangeException(getTaskId() + ": couldn't read the stored settings", e);
        }
    }

    /** Step configs: rows keyed by config id and name. */
    private int renameStepSettings() throws SQLException {
        List<String> parameters = new ArrayList<>(List.of(WidgetAppearance.LEGACY_KEY, WidgetAppearance.KEY));
        parameters.addAll(WidgetAppearanceMigration.STEP_PROVIDERS);
        String providers = String.join(", ", Collections.nCopies(WidgetAppearanceMigration.STEP_PROVIDERS.size(), "?"));
        List<Row> rows = read("SELECT c.AUTHENTICATOR_ID, c.AUTHENTICATOR_ID, c.NAME, c.VALUE FROM " + getTableName("AUTHENTICATOR_CONFIG_ENTRY") + " c"
                        + " WHERE c.NAME IN (?, ?) AND c.AUTHENTICATOR_ID IN (SELECT e.AUTH_CONFIG FROM " + getTableName("AUTHENTICATION_EXECUTION") + " e"
                        + " WHERE e.AUTHENTICATOR IN (" + providers + "))",
                parameters.toArray(String[]::new));
        Plan plan = WidgetAppearanceMigration.plan(rows);

        String table = database.correctObjectName("AUTHENTICATOR_CONFIG_ENTRY", Table.class);
        for (Row row : plan.renames()) {
            statements.add(new UpdateStatement(null, null, table)
                    .addNewColumnValue("NAME", row.name())
                    .addNewColumnValue("VALUE", row.value())
                    .setWhereClause("AUTHENTICATOR_ID=? AND NAME=?")
                    .addWhereParameter(row.owner())
                    .addWhereParameter(WidgetAppearance.LEGACY_KEY));
        }
        for (Row row : plan.deletes()) {
            statements.add(new DeleteStatement(null, null, table)
                    .setWhere("AUTHENTICATOR_ID=? AND NAME=?")
                    .addWhereParameter(row.owner())
                    .addWhereParameter(WidgetAppearance.LEGACY_KEY));
        }
        return plan.renames().size() + plan.deletes().size();
    }

    /** The realm settings component: rows have ids of their own, and a key may have several. */
    private int renameRealmSettings() throws SQLException {
        List<Row> rows = read("SELECT c.ID, c.COMPONENT_ID, c.NAME, c.VALUE FROM " + getTableName("COMPONENT_CONFIG") + " c"
                        + " WHERE c.NAME IN (?, ?) AND c.COMPONENT_ID IN (SELECT m.ID FROM " + getTableName("COMPONENT") + " m"
                        + " WHERE m.PROVIDER_ID = ? AND m.PROVIDER_TYPE = ?)",
                WidgetAppearance.LEGACY_KEY, WidgetAppearance.KEY, CloudflareTurnstileRealmSettings.TAB_ID, UiTabProvider.class.getName());
        Plan plan = WidgetAppearanceMigration.plan(rows);

        String table = database.correctObjectName("COMPONENT_CONFIG", Table.class);
        for (Row row : plan.renames()) {
            statements.add(new UpdateStatement(null, null, table)
                    .addNewColumnValue("NAME", row.name())
                    .addNewColumnValue("VALUE", row.value())
                    .setWhereClause("ID=?")
                    .addWhereParameter(row.id()));
        }
        for (Row row : plan.deletes()) {
            statements.add(new DeleteStatement(null, null, table)
                    .setWhere("ID=?")
                    .addWhereParameter(row.id()));
        }
        return plan.renames().size() + plan.deletes().size();
    }

    /** Rows of a query selecting id, owner, name and value. */
    private List<Row> read(String sql, String... parameters) throws SQLException {
        List<Row> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                ps.setString(i + 1, parameters[i]);
            }
            try (ResultSet resultSet = ps.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new Row(resultSet.getString(1), resultSet.getString(2), resultSet.getString(3), resultSet.getString(4)));
                }
            }
        }
        return rows;
    }

    @Override
    protected String getTaskId() {
        return "Rename the Cloudflare Turnstile setting " + WidgetAppearance.LEGACY_KEY + " to " + WidgetAppearance.KEY;
    }
}

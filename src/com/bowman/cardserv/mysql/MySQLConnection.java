package com.bowman.cardserv.mysql;

import com.bowman.cardserv.interfaces.MySQLConstants;
import com.bowman.cardserv.util.ProxyLogger;
import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;

import java.io.IOException;
import java.sql.*;
import java.util.*;

/**
 * A simple database connection object.
 *
 * @author DonCarlo
 * @since 05.02.2011
 */
public class MySQLConnection implements MySQLConstants {

  private ProxyLogger logger = null;

  private Connection connection;
  private ResultSet resultSet;
  private long inactivityTimestamp;
  private MysqlConnectionPoolDataSource dataSource;

  private PreparedStatement ps_getUserByName, ps_addUser, ps_editUser, ps_deleteUserByName, ps_deleteAllUsers,
      ps_setUserDebug, ps_getUserNames, ps_getProfileById, ps_getProfileByName, ps_getProfiles, ps_addProfile,
      ps_editProfile, ps_deleteProfile, ps_deleteAllProfiles, ps_addUserProfiles, ps_deleteUserProfiles, ps_existsTable;

  public MySQLConnection(MysqlConnectionPoolDataSource dataSource) throws IOException {
    this.dataSource = dataSource;
    initialize();
  }

  private void initialize() throws IOException {
    this.logger = ProxyLogger.getLabeledLogger(getClass().getName());
    establishConnection();
    if(connection == null) throw new IOException("Failed to establish mysql connection.");
    setPreparedStatements();
  }

  private void setPreparedStatements() {
    try {
      // users
      ps_getUserByName = connection.prepareStatement(PS_GET_USER_BY_NAME);
      ps_addUser = connection.prepareStatement(PS_ADD_USER, Statement.RETURN_GENERATED_KEYS);
      ps_editUser = connection.prepareStatement(PS_EDIT_USER, Statement.RETURN_GENERATED_KEYS);
      ps_deleteUserByName = connection.prepareStatement(PS_DELETE_USER_BY_NAME, Statement.RETURN_GENERATED_KEYS);
      ps_deleteAllUsers = connection.prepareStatement(PS_DELETE_ALL_USERS, Statement.RETURN_GENERATED_KEYS);
      ps_setUserDebug = connection.prepareStatement(PS_SET_USER_DEBUG);
      ps_getUserNames = connection.prepareStatement(PS_GET_USERNAMES);
      // profiles
      ps_getProfileById = connection.prepareStatement(PS_GET_PROFILE_BY_ID);
      ps_getProfileByName = connection.prepareStatement(PS_GET_PROFILE_BY_NAME);
      ps_getProfiles = connection.prepareStatement(PS_GET_PROFILES);
      ps_addProfile = connection.prepareStatement(PS_ADD_PROFILE, Statement.RETURN_GENERATED_KEYS);
      ps_editProfile = connection.prepareStatement(PS_EDIT_PROFILE, Statement.RETURN_GENERATED_KEYS);
      ps_deleteProfile = connection.prepareStatement(PS_DELETE_PROFILE, Statement.RETURN_GENERATED_KEYS);
      ps_deleteAllProfiles = connection.prepareStatement(PS_DELETE_ALL_PROFILES, Statement.RETURN_GENERATED_KEYS);
      ps_addUserProfiles = connection.prepareStatement(PS_ADD_USER_PROFILES, Statement.RETURN_GENERATED_KEYS);
      ps_deleteUserProfiles = connection.prepareStatement(PS_DELETE_USER_PROFILES, Statement.RETURN_GENERATED_KEYS);
      // miscellaneous
      ps_existsTable = connection.prepareStatement(PS_EXISTS_TABLE);
    } catch(SQLException e) {
      logger.severe("(setPreparedStatements) Failed to setup prepared statements: " + e);
    }
  }

  private void establishConnection() {
    try {
      Class.forName("com.mysql.jdbc.Driver");
      connection = dataSource.getPooledConnection().getConnection();
    } catch(ClassNotFoundException e) {
      logger.severe("(initialize) class 'com.mysql.jdbc.Driver' not found: " + e);
    } catch(SQLException e) {
      logger.severe("(initialize) Failed to setup database connection: " + e);
    }
  }

  private void setInactivityTimestamp() {
    inactivityTimestamp = System.currentTimeMillis();
  }

  public boolean isHealthy() {
    boolean isHealthy = false;
    try {
      isHealthy = !connection.isClosed() && connection.getWarnings() == null;
    } catch(SQLException e) {
      logger.warning("(isHealthy) Failed to check connection healthiness: " + e);
    }
    return isHealthy;
  }

  public boolean isInactive(long inactiveTime) {
    return System.currentTimeMillis() - inactivityTimestamp > inactiveTime;
  }

  public void closeConnection() {
    try {
      // users
      if(ps_getUserByName != null) ps_getUserByName.close();
      if(ps_addUser != null) ps_addUser.close();
      if(ps_editUser != null) ps_editUser.close();
      if(ps_deleteUserByName != null) ps_deleteUserByName.close();
      if(ps_deleteAllUsers != null) ps_deleteAllUsers.close();
      if(ps_setUserDebug != null) ps_setUserDebug.close();
      if(ps_getUserNames != null) ps_getUserNames.close();
      // profiles
      if(ps_getProfileById != null) ps_getProfileById.close();
      if(ps_getProfileByName != null) ps_getProfileByName.close();
      if(ps_getProfiles != null) ps_getProfiles.close();
      if(ps_addProfile != null) ps_addProfile.close();
      if(ps_editProfile != null) ps_editProfile.close();
      if(ps_deleteProfile != null) ps_deleteProfile.close();
      if(ps_deleteAllProfiles != null) ps_deleteAllProfiles.close();
      if(ps_addUserProfiles != null) ps_addUserProfiles.close();
      if(ps_deleteUserProfiles != null) ps_deleteUserProfiles.close();
      // miscellaneous
      if(ps_existsTable != null) ps_existsTable.close();
    } catch(SQLException e) {
      logger.warning("(closeConnection) Failed to close database connection: " + e);
    }

  }

  public ResultSet getResultSet() {
    return resultSet;
  }

  private boolean executeQuery(PreparedStatement ps) {
    try {
      resultSet = ps.executeQuery();
      ps.clearParameters();
      return true;
    } catch(SQLException e) {
      logger.warning("(executeQuery) Failed to query the database: " + e);
    } finally {
      setInactivityTimestamp();
    }
    return false;
  }

  private boolean executeUpdate(PreparedStatement ps) {
    try {
      ps.executeUpdate();
      resultSet = ps.getGeneratedKeys();
      ps.clearParameters();
      return true;
    } catch(SQLException e) {
      logger.warning("(executeUpdate) Failed to update the database: " + e);
    } finally {
      setInactivityTimestamp();
    }
    return false;
  }

  private void closeResultSet() {
    try {
      if(resultSet != null) {
        resultSet.close();
        resultSet = null;
      }
    } catch(Exception e) {
      logger.warning("(closeResultSet) error while closing resultSet: " + e);
    }
  }

  public void returnConnection() {
    closeResultSet();
  }

  public boolean getUser(String userName) {
    try {
      logger.fine("(getUserByName) getting user: " + userName + " from database.");
      ps_getUserByName.setString(1, userName);
      return executeQuery(ps_getUserByName);
    } catch(SQLException e) {
      logger.warning("(getUserByName) Failed to set preparedStatement parameters: " + e);
      return false;
    }
  }

  public boolean addUser(String username, String password, String displayname, String ipmask, int maxconnections,
                         boolean enabled, boolean debug, boolean admin, String mail, boolean mapexcluded)
  {
    try {
      logger.fine("(addUser) adding user: " + username + " to database.");
      ps_addUser.setString(1, username);
      ps_addUser.setString(2, password);
      ps_addUser.setString(3, displayname);
      ps_addUser.setString(4, ipmask);
      ps_addUser.setInt(5, maxconnections);
      ps_addUser.setBoolean(6, enabled);
      ps_addUser.setBoolean(7, debug);
      ps_addUser.setBoolean(8, admin);
      ps_addUser.setString(9, mail);
      ps_addUser.setBoolean(10, mapexcluded);
      return executeUpdate(ps_addUser);
    } catch(SQLException e) {
      logger.warning("(addUser) Failed to set preparedStatement parameters: " + e);
      return false;
    }
  }

  public boolean editUser(int id, String username, String password, String displayname, String ipmask, int maxconnections,
                          boolean enabled, boolean debug, boolean admin, String mail, boolean mapexcluded)
  {
    try {
      logger.fine("(editUser) editing user: " + id + " in database.");
      ps_editUser.setString(1, username);
      ps_editUser.setString(2, password);
      ps_editUser.setString(3, displayname);
      ps_editUser.setString(4, ipmask);
      ps_editUser.setInt(5, maxconnections);
      ps_editUser.setBoolean(6, enabled);
      ps_editUser.setBoolean(7, debug);
      ps_editUser.setBoolean(8, admin);
      ps_editUser.setString(9, mail);
      ps_editUser.setBoolean(10, mapexcluded);
      ps_editUser.setInt(11, id);
      return executeUpdate(ps_editUser);
    } catch(SQLException e) {
      logger.warning("(editUser) Failed to set preparedStatement parameters: " + e);
      return false;
    }
  }

  public boolean deleteUserByName(String userName) {
    try {
      logger.fine("(deleteUserByName) deleting user: " + userName + " from database.");
      ps_deleteUserByName.setString(1, userName);
      return executeUpdate(ps_deleteUserByName);
    } catch(SQLException e) {
      logger.warning("(deleteUserByName) Failed to set preparedStatement parameters: " + e);
      return false;
    }
  }

  public boolean deleteAllUsers(String skipUserName) {
    try {
      logger.fine("(deleteAllUsers) deleting all users except: " + skipUserName + " from database.");
      ps_deleteAllUsers.setString(1, skipUserName);
      return executeUpdate(ps_deleteAllUsers);
    } catch(SQLException e) {
      logger.warning("(deleteAllUsers) Failed to set preparedStatement parameters: " + e);
      return false;
    }
  }

  public boolean setUserDebug(String username, boolean debug) {
    try {
      logger.fine("(setUserDebug) setting debug to: " + debug + " for user: " + username);
      ps_setUserDebug.setBoolean(1, debug);
      ps_setUserDebug.setString(2, username);
      return executeUpdate(ps_setUserDebug);
    } catch(SQLException e) {
      logger.warning("(setUserDebug) Failed to set preparedStatement parameters: " + e);
      return false;
    }
  }

  public boolean getUserNames(int skipRows, int numRows) {
    try {
      logger.fine("(getUserNames) getting usernames from database. Limited by skipRows: " + skipRows + " numRows: " + numRows);
      ps_getUserNames.setInt(1, skipRows);
      ps_getUserNames.setInt(2, numRows);
      return executeQuery(ps_getUserNames);
    } catch(SQLException e) {
      logger.warning("(getUserNames) Failed to set preparedStatement parameters: " + e);
      return false;
    }
  }

  public boolean getProfile(int id) {
    try {
      logger.fine("(getProfile) getting profile: " + id + " from database.");
      ps_getProfileById.setInt(1, id);
      return executeQuery(ps_getProfileById);
    } catch(SQLException e) {
      logger.warning("(getProfile) Failed to set preparedStatement parameters: " + e);
      return false;
    }
  }

  public boolean getProfile(String profileName) {
    try {
      logger.fine("(getProfile) getting profile: " + profileName + " from database.");
      ps_getProfileByName.setString(1, profileName);
      return executeQuery(ps_getProfileByName);
    } catch(SQLException e) {
      logger.warning("(getProfile) Failed to set preparedStatement parameters: " + e);
      return false;
    }
  }

  public boolean getProfiles() {
    logger.fine("(getProfiles) getting profiles from database.");
    return executeQuery(ps_getProfiles);
  }

  public boolean addProfile(String profileName) {
    try {
      logger.fine("(addProfile) adding profile: " + profileName + " to database.");
      ps_addProfile.setString(1, profileName);
      return executeUpdate(ps_addProfile);
    } catch(SQLException e) {
      logger.warning("(addProfile) Failed to set preparedStatement parameters: " + e);
      return false;
    }
  }

  public boolean editProfile(int id, String profileName) {
    try {
      logger.fine("(editProfile) editing profile: " + id + " in database.");
      ps_editProfile.setString(1, profileName);
      ps_editProfile.setInt(2, id);
      return executeUpdate(ps_editProfile);
    } catch(SQLException e) {
      logger.warning("(editProfile) Failed to set preparedStatement parameters: " + e);
      return false;
    }
  }

  public boolean deleteProfile(int id) {
    try {
      logger.fine("(deleteProfile) deleting profile: " + id + " from database.");
      ps_deleteProfile.setInt(1, id);
      return executeUpdate(ps_deleteProfile);
    } catch(SQLException e) {
      logger.warning("(deleteProfile) Failed to set preparedStatement parameters: " + e);
      return false;
    }
  }

  public boolean deleteAllProfiles() {
    logger.fine("(deleteAllProfiles) deleting all profiles from database.");
    return executeUpdate(ps_deleteAllProfiles);
  }

  public boolean addUserProfiles(int userId, Set profileIds) {
    try {
      logger.fine("(addUserProfiles) adding user profiles to database.");
      Iterator iterator = profileIds.iterator();
      while(iterator.hasNext()) {
        ps_addUserProfiles.setInt(1, userId);
        ps_addUserProfiles.setInt(2, ((Integer)iterator.next()).intValue());
        ps_addUserProfiles.addBatch();
      }
      boolean result = true;
      int[] counts = ps_addUserProfiles.executeBatch();
      for(int i = 0; i < counts.length; i++) {
        result &= result && counts[i] >= 0;
      }
      return result;
    } catch(SQLException e) {
      logger.warning("(addUserProfiles) Failed to set preparedStatement parameters: " + e);
      return false;
    }
  }

  public boolean deleteUserProfiles(int userId) {
    try {
      logger.fine("(deleteUserProfiles) deleting user (" + userId + ") profiles from database.");
      ps_deleteUserProfiles.setInt(1, userId);
      return executeUpdate(ps_deleteUserProfiles);
    } catch(SQLException e) {
      logger.warning("(deleteUserProfiles) Failed to set preparedStatement parameters: " + e);
      return false;
    }
  }

  private boolean existsTable(String database, String table) {
    try {
      logger.fine("(existsTable) exists table: " + table + " in database.");
      ps_existsTable.setString(1, database);
      ps_existsTable.setString(2, table);
      return executeQuery(ps_existsTable) && resultSet.next() && resultSet.getString(1).equalsIgnoreCase(table);
    } catch(SQLException e) {
      logger.warning("(existsTable) Failed to set preparedStatement parameters: " + e);
      return false;
    }
  }

  public boolean existsUserTable() {
    return existsTable(dataSource.getDatabaseName(), DBT_USERS);
  }

  public boolean existsProfileTable() {
    return existsTable(dataSource.getDatabaseName(), DBT_PROFILES);
  }

  public boolean existsUsersHasProfilesTable() {
    return existsTable(dataSource.getDatabaseName(), DBT_USERS_HAS_PROFILES);
  }

  public void createUserTable() {
    try {
      Statement statement = connection.createStatement();
      statement.executeUpdate(CREATE_USER_TABLE_STMT);
      statement.close();
    } catch(SQLException e) {
      logger.warning("(createUserTable) Failed to create table.");
    }
  }

  public void createProfileTable() {
    try {
      Statement statement = connection.createStatement();
      statement.executeUpdate(CREATE_PROFILE_TABLE_STMT);
      statement.close();
    } catch(SQLException e) {
      logger.warning("(createProfileTable) Failed to create table.");
    }
  }

  public void createUsersHasProfilesTable() {
    try {
      Statement statement = connection.createStatement();
      statement.executeUpdate(CREATE_USERS_HAS_PROFILES_TABLE_STMT);
      statement.close();
    } catch(SQLException e) {
      logger.warning("(createUsersHasProfilesTable) Failed to create table.");
    }
  }

}

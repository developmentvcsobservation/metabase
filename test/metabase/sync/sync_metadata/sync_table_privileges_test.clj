(ns ^:mb/driver-tests metabase.sync.sync-metadata.sync-table-privileges-test
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.set :as set]
   [clojure.test :refer :all]
   [metabase.driver.sql-jdbc.connection :as sql-jdbc.conn]
   [metabase.sync.sync-metadata.sync-table-privileges :as sync-table-privileges]
   [metabase.sync.sync-metadata.tables :as sync-tables]
   [metabase.test :as mt]
   [toucan2.core :as t2]))

(set! *warn-on-reflection* true)

(deftest ^:synchronized sync-table-privileges!-test
  (mt/test-drivers (set/intersection (mt/normal-drivers-with-feature :table-privileges)
                                     (mt/normal-drivers-with-feature :schemas))
    (testing "`TablePrivileges` should store the correct data for current_user and role privileges for databases with schemas"
      (mt/with-empty-db
        (let [conn-spec (sql-jdbc.conn/db->pooled-connection-spec (mt/db))]
          (jdbc/execute! conn-spec (str "DROP SCHEMA IF EXISTS foo; "
                                        "CREATE SCHEMA foo; "
                                        "CREATE TABLE foo.baz (id INTEGER);"))
          (sync-tables/sync-tables-and-database! (mt/db))
          (let [synced-privileges (atom nil)
                original-sync-table-privileges! sync-table-privileges/sync-table-privileges!]
            (with-redefs [sync-table-privileges/sync-table-privileges!
                          (fn [& args]
                            (let [{:keys [total-table-privileges]} (apply original-sync-table-privileges! args)]
                              (reset! synced-privileges total-table-privileges)))]
              (sync-table-privileges/sync-table-privileges! (mt/db))
              (testing "Correct number of privileges synced with batch size in use"
                (is (= 1 @synced-privileges)))))
          (let [table-id (t2/select-one-pk :model/Table :name "baz" :schema "foo")]
            (is (=? [{:table_id        table-id
                      :role            nil
                      :select          true
                      :delete          true
                      :insert          true
                      :update          true}]
                    (t2/select :model/TablePrivileges :table_id table-id :role nil))))))))
  (mt/test-drivers (set/difference (mt/normal-drivers-with-feature :table-privileges)
                                   (mt/normal-drivers-with-feature :schemas))
    (testing "`TablePrivileges` should store the correct data for current_user and role privileges for databases without schemas"
      (mt/with-empty-db
        (let [conn-spec (sql-jdbc.conn/db->pooled-connection-spec (mt/db))]
          (jdbc/execute! conn-spec "CREATE TABLE baz (id INTEGER);")
          (sync-tables/sync-tables-and-database! (mt/db))
          (let [synced-privileges (atom nil)
                original-sync-table-privileges! sync-table-privileges/sync-table-privileges!]
            (with-redefs [sync-table-privileges/sync-table-privileges!
                          (fn [& args]
                            (let [{:keys [total-table-privileges]} (apply original-sync-table-privileges! args)]
                              (reset! synced-privileges total-table-privileges)))]
              (sync-table-privileges/sync-table-privileges! (mt/db))
              (testing "Correct number of privileges synced with batch size in use"
                (is (= 1 @synced-privileges)))))
          (let [table-id (t2/select-one-pk :model/Table :name "baz" :schema nil)]
            (is (=? [{:table_id        table-id
                      :role            nil
                      :select          true
                      :delete          true
                      :insert          true
                      :update          true}]
                    (t2/select :model/TablePrivileges :table_id table-id :role nil)))))))))

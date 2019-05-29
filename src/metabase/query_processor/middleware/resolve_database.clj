(ns metabase.query-processor.middleware.resolve-database
  (:require [metabase.mbql.schema :as mbql.s]
            [metabase.models.database :as database :refer [Database]]
            [metabase.query-processor.store :as qp.store]
            [metabase.util :as u]
            [metabase.util.i18n :refer [tru]]
            [toucan.db :as db]))

(defn- resolve-database* [{database-id :database, :as query}]
  (u/prog1 query
    (when-not (= database-id mbql.s/saved-questions-virtual-database-id)
      (qp.store/store-database! (or (db/select-one (apply vector Database qp.store/database-columns-to-fetch)
                                      :id (u/get-id database-id))
                                    (throw (ex-info (str (tru "Database {0} does not exist." database-id))
                                             {:database database-id})))))))

(defn resolve-database
  "Middleware that resolves the Database referenced by the query under that `:database` key and stores it in the QP
  Store."
  [qp]
  (comp qp resolve-database*))

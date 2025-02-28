(ns metabase.notification.core
  "Core functionality for notifications."
  (:require
   [metabase.notification.payload.core :as notification.payload]
   [metabase.notification.seed :as notification.seed]
   [metabase.notification.send :as notification.send]
   [metabase.util :as u]
   [metabase.util.log :as log]
   [metabase.util.malli :as mu]
   [potemkin :as p]))

;; ------------------------------------------------------------------------------------------------;;
;;                                           Public APIs                                           ;;
;; ------------------------------------------------------------------------------------------------;;

(p/import-vars
 [notification.payload
  notification-payload
  Notification
  NotificationPayload]
 [notification.seed
  truncate-then-seed-notification!])

(def ^:private Options
  [:map
   [:notification/sync? :boolean]])

(def ^:dynamic *default-options*
  "The default options for sending a notification."
  {:notification/sync? false})

(mu/defn send-notification!
  "The function to send a notification. Defaults to `notification.send/send-notification-async!`."
  [notification & {:keys [] :as options} :- [:maybe Options]]
  (let [options (merge *default-options* options)]
    (log/debugf "Sending notification: %s %s" (:id notification) (if (:notification/sync? options) "synchronously" "asynchronously"))
    (if (:notification/sync? options)
      (notification.send/send-notification-sync! notification)
      (notification.send/send-notification-async! notification))))

;; ------------------------------------------------------------------------------------------------;;
;;                                    Load the implementations                                     ;;
;; ------------------------------------------------------------------------------------------------;;
(when-not *compile-files*
  ;; load payload impls here rather than payload.core.
  ;; the impls depend on payload.core and would cause a cyclic dependency
  (u/find-and-load-namespaces! "metabase.notification.payload.impl"))

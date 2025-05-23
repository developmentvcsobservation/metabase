(ns metabase.timeline.models.timeline-event-test
  "Tests for TimelineEvent model namespace."
  (:require
   [clojure.test :refer :all]
   [metabase.test :as mt]
   [metabase.timeline.models.timeline-event :as timeline-event]
   [metabase.util :as u]
   [toucan2.core :as t2]))

(defn- names [timelines]
  (into #{} (comp (mapcat :events) (map :name)) timelines))

(deftest hydrate-events-test
  (testing "hydrate-events function hydrates all timelines events"
    (mt/with-temp [:model/Collection _collection {:name "Rasta's Collection"}
                   :model/Timeline tl-a {:name "tl-a"}
                   :model/Timeline tl-b {:name "tl-b"}
                   :model/TimelineEvent _ {:timeline_id (u/the-id tl-a) :name "un-1"}
                   :model/TimelineEvent _ {:timeline_id (u/the-id tl-a) :name "archived-1"
                                           :archived true}
                   :model/TimelineEvent _ {:timeline_id (u/the-id tl-b) :name "un-2"}
                   :model/TimelineEvent _ {:timeline_id (u/the-id tl-b) :name "archived-2"
                                           :archived true}]
      (testing "only unarchived events by default"
        (is (= #{"un-1" "un-2"}
               (names (timeline-event/include-events [tl-a tl-b] {})))))
      (testing "all events when specified"
        (is (= #{"un-1" "un-2" "archived-1" "archived-2"}
               (names (timeline-event/include-events [tl-a tl-b] {:events/all? true}))))))))

(deftest balloon-icon-migration-test
  (testing "timeline events with icon=balloons should use the default icon instead when selected"
    (mt/with-temp [:model/Timeline tl-a {:icon "balloons"}
                   :model/Timeline tl-b {:icon "cake"}
                   :model/TimelineEvent a {:timeline_id (u/the-id tl-a) :icon "balloons"}
                   :model/TimelineEvent b {:timeline_id (u/the-id tl-b) :icon "cake"}]
      (is (= timeline-event/default-icon
             (t2/select-one-fn :icon :model/TimelineEvent (u/the-id a))))
      (is (= "cake"
             (t2/select-one-fn :icon :model/TimelineEvent (u/the-id b)))))))

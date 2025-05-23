(ns metabase.public-settings-test
  (:require
   [clojure.test :refer :all]
   [metabase.config :as config]
   [metabase.models.setting :as setting]
   [metabase.premium-features.core :as premium-features]
   [metabase.public-settings :as public-settings]
   [metabase.test :as mt]
   [metabase.test.fixtures :as fixtures]
   [metabase.util.i18n :as i18n :refer [tru]]))

(set! *warn-on-reflection* true)

(use-fixtures :once (fixtures/initialize :db))

(deftest site-url-settings
  (testing "double-check that setting the `site-url` setting will automatically strip off trailing slashes"
    (mt/discard-setting-changes [site-url]
      (public-settings/site-url! "http://localhost:3000/")
      (is (= "http://localhost:3000"
             (public-settings/site-url))))))

(deftest site-url-settings-prepend-http
  (testing "double-check that setting the `site-url` setting will prepend `http://` if no protocol was specified"
    (mt/discard-setting-changes [site-url]
      (public-settings/site-url! "localhost:3000")
      (is (= "http://localhost:3000"
             (public-settings/site-url))))))

(deftest site-url-settings-with-no-trailing-slash
  (mt/discard-setting-changes [site-url]
    (public-settings/site-url! "http://localhost:3000")
    (is (= "http://localhost:3000"
           (public-settings/site-url)))))

(deftest site-url-settings-https
  (testing "if https:// was specified it should keep it"
    (mt/discard-setting-changes [site-url]
      (public-settings/site-url! "https://localhost:3000")
      (is (= "https://localhost:3000"
             (public-settings/site-url))))))

(deftest site-url-settings-validate-site-url
  (testing "we should not be allowed to set an invalid `site-url` (#9850)"
    (mt/discard-setting-changes [site-url]
      (is (thrown?
           clojure.lang.ExceptionInfo
           (public-settings/site-url! "http://https://www.camsaul.com"))))))

(deftest site-url-settings-set-valid-domain-name
  (mt/discard-setting-changes [site-url]
    (is (some? (public-settings/site-url! "https://www.camsaul.x")))))

(deftest site-url-settings-nil-getter-when-invalid
  (testing "if `site-url` in the database is invalid, the getter for `site-url` should return `nil` (#9849)"
    (mt/discard-setting-changes [site-url]
      (setting/set-value-of-type! :string :site-url "https://&")
      (is (= "https://&"
             (setting/get-value-of-type :string :site-url)))
      (is (= nil
             (public-settings/site-url))))))

(deftest site-url-settings-normalize
  (testing "We should normalize `site-url` when set via env var we should still normalize it (#9764)"
    (mt/with-temp-env-var-value! [mb-site-url "localhost:3000/"]
      (is (= "localhost:3000/"
             (setting/get-value-of-type :string :site-url)))
      (is (= "http://localhost:3000"
             (public-settings/site-url))))))

(deftest invalid-site-url-env-var-test
  (testing (str "If `site-url` is set via an env var, and it's invalid, we should return `nil` rather than having the"
                " whole instance break")
    (mt/with-temp-env-var-value! [mb-site-url "asd_12w31%$;"]
      (is (= "asd_12w31%$;"
             (setting/get-value-of-type :string :site-url)))
      (is (= nil
             (public-settings/site-url))))))

(deftest site-url-should-update-https-redirect-test
  (testing "Changing `site-url` to non-HTTPS should disable forced HTTPS redirection"
    (mt/with-temporary-setting-values [site-url                       "https://example.com"
                                       redirect-all-requests-to-https true]
      (is (true?
           (public-settings/redirect-all-requests-to-https)))
      (public-settings/site-url! "http://example.com")
      (is (= false
             (public-settings/redirect-all-requests-to-https)))))

  (testing "Changing `site-url` to non-HTTPS should disable forced HTTPS redirection"
    (mt/with-temporary-setting-values [site-url                       "https://example.com"
                                       redirect-all-requests-to-https true]
      (is (true?
           (public-settings/redirect-all-requests-to-https)))
      (public-settings/site-url! "https://different.example.com")
      (is (true?
           (public-settings/redirect-all-requests-to-https))))))

(deftest translate-public-setting
  (mt/with-mock-i18n-bundles! {"zz" {:messages {"Host" "HOST"}}}
    (mt/with-user-locale "zz"
      (is (= "HOST"
             (str (get-in (setting/user-readable-values-map #{:public})
                          [:engines :postgres :details-fields 0 :display-name])))))))

(deftest tru-translates
  (mt/with-mock-i18n-bundles! {"zz" {:messages {"Host" "HOST"}}}
    (mt/with-user-locale "zz"
      (is (true?
           (= (i18n/locale "zz")
              (i18n/user-locale))))
      (is (= "HOST"
             (tru "Host"))))))

(deftest query-caching-max-kb-test
  (testing (str "Make sure Max Cache Entry Size can be set via with a string value, which is what comes back from the "
                "API (#9143)")
    (mt/discard-setting-changes [query-caching-max-kb]
      (is (= "1000"
             (public-settings/query-caching-max-kb! "1000")))))

  (testing "query-caching-max-kb should throw an error if you try to put in a huge value"
    (mt/discard-setting-changes [query-caching-max-kb]
      (is (thrown-with-msg?
           IllegalArgumentException
           #"Values greater than 204,800 \(200\.0 MB\) are not allowed"
           (public-settings/query-caching-max-kb! (* 1024 1024)))))))

(deftest site-locale-validate-input-test
  (testing "site-locale should validate input"
    (testing "blank string"
      (mt/with-temporary-setting-values [site-locale "en_US"]
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Invalid locale \"\""
             (public-settings/site-locale! "")))
        (is (= "en_US"
               (public-settings/site-locale)))))
    (testing "non-existant locale"
      (mt/with-temporary-setting-values [site-locale "en_US"]
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Invalid locale \"en_EN\""
             (public-settings/site-locale! "en_EN")))
        (is (= "en_US"
               (public-settings/site-locale)))))))

(deftest site-locale-normalize-input-test
  (testing "site-locale should normalize input"
    (mt/discard-setting-changes [site-locale]
      (public-settings/site-locale! "en-us")
      (is (= "en_US"
             (public-settings/site-locale))))))

(deftest unset-site-locale-test
  (testing "should be able to unset site-locale"
    (mt/discard-setting-changes [site-locale]
      (public-settings/site-locale! "es")
      (public-settings/site-locale! nil)
      (is (= "en"
             (public-settings/site-locale))
          "should default to English"))))

(deftest site-locale-only-return-valid-locales-test
  (mt/with-temporary-raw-setting-values [site-locale "wow_this_in_not_a_locale"]
    (is (nil? (public-settings/site-locale)))))

(deftest redirect-all-requests-to-https-test
  (testing "Shouldn't be allowed to set `redirect-all-requests-to-https` to `true` unless `site-url` is HTTPS"
    (doseq [v [true "true"]]
      (testing (format "\nSet value to ^%s %s" (.getCanonicalName (class v)) (pr-str v))
        (testing "\n`site-url` *is* HTTPS"
          (mt/with-temporary-setting-values [site-url                       "https://example.com"
                                             redirect-all-requests-to-https false]
            (public-settings/redirect-all-requests-to-https! v)
            (is (true?
                 (public-settings/redirect-all-requests-to-https)))))

        (testing "\n`site-url` is not HTTPS"
          (mt/with-temporary-setting-values [site-url                       "http://example.com"
                                             redirect-all-requests-to-https false]
            (is (thrown?
                 AssertionError
                 (public-settings/redirect-all-requests-to-https! v)))
            (is (= false
                   (public-settings/redirect-all-requests-to-https)))))))))

(deftest cloud-gateway-ips-test
  (mt/with-temp-env-var-value! [mb-cloud-gateway-ips "1.2.3.4,5.6.7.8"]
    (with-redefs [premium-features/is-hosted? (constantly true)]
      (testing "Setting returns ips given comma delimited ips."
        (is (= ["1.2.3.4" "5.6.7.8"]
               (public-settings/cloud-gateway-ips)))))

    (testing "Setting returns nil in self-hosted environments"
      (with-redefs [premium-features/is-hosted? (constantly false)]
        (is (= nil (public-settings/cloud-gateway-ips)))))))

(deftest start-of-week-test
  (mt/discard-setting-changes [start-of-week]
    (testing "Error on invalid value"
      (is (thrown-with-msg?
           Throwable
           #"Invalid day of week: :fraturday"
           (public-settings/start-of-week! :fraturday))))
    (mt/with-temp-env-var-value! [start-of-week nil]
      (testing "Should default to Sunday"
        (is (= :sunday
               (public-settings/start-of-week))))
      (testing "Sanity check: make sure we're setting the env var value correctly for the assertion after this"
        (mt/with-temp-env-var-value! [:mb-start-of-week "monday"]
          (is (= :monday
                 (public-settings/start-of-week)))))
      (testing "Fall back to default if value is invalid"
        (mt/with-temp-env-var-value! [:mb-start-of-week "fraturday"]
          (is (= :sunday
                 (public-settings/start-of-week))))))))

(deftest help-link-setting-test
  (mt/discard-setting-changes [help-link]
    (mt/with-premium-features #{:whitelabel}
      (testing "When whitelabeling is enabled, help-link setting can be set to any valid value"
        (public-settings/help-link! :metabase)
        (is (= :metabase (public-settings/help-link)))

        (public-settings/help-link! :hidden)
        (is (= :hidden (public-settings/help-link)))

        (public-settings/help-link! :custom)
        (is (= :custom (public-settings/help-link))))

      (testing "help-link cannot be set to an invalid value"
        (is (thrown-with-msg?
             Exception #"Invalid help link option"
             (public-settings/help-link! :invalid)))))

    (mt/with-premium-features #{}
      (testing "When whitelabeling is not enabled, help-link setting cannot be set, and always returns :metabase"
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Setting help-link is not enabled because feature :whitelabel is not available"
             (public-settings/help-link! :hidden)))

        (is (= :metabase (public-settings/help-link)))))))

(deftest validate-help-url-test
  (testing "validate-help-url accepts valid URLs with HTTP or HTTPS protocols"
    (is (nil? (#'public-settings/validate-help-url "http://www.metabase.com")))
    (is (nil? (#'public-settings/validate-help-url "https://www.metabase.com"))))

  (testing "validate-help-url accepts valid mailto: links"
    (is (nil? (#'public-settings/validate-help-url "mailto:help@metabase.com"))))

  (testing "validate-help-url rejects malformed URLs and URLs with invalid protocols"
    ;; Since validate-help-url calls `u/url?` to validate URLs, we don't need to test all possible malformed URLs here.
    (is (thrown-with-msg?
         Exception
         #"Please make sure this is a valid URL"
         (#'public-settings/validate-help-url "asdf")))

    (is (thrown-with-msg?
         Exception
         #"Please make sure this is a valid URL"
         (#'public-settings/validate-help-url "ftp://metabase.com"))))

  (testing "validate-help-url rejects mailto: links with invalid email addresses"
    (is (thrown-with-msg?
         Exception
         #"Please make sure this is a valid URL"
         (#'public-settings/validate-help-url "mailto:help@metabase")))))

(deftest help-link-custom-destination-setting-test
  (mt/with-premium-features #{:whitelabel}
    (testing "When whitelabeling is enabled, help-link-custom-destination can be set to valid URLs"
      (public-settings/help-link-custom-destination! "http://www.metabase.com")
      (is (= "http://www.metabase.com" (public-settings/help-link-custom-destination)))

      (public-settings/help-link-custom-destination! "mailto:help@metabase.com")
      (is (= "mailto:help@metabase.com" (public-settings/help-link-custom-destination))))

    (testing "help-link-custom-destination cannot be set to invalid URLs"
      (is (thrown-with-msg?
           Exception
           #"Please make sure this is a valid URL"
           (public-settings/help-link-custom-destination! "asdf")))

      (is (thrown-with-msg?
           Exception
           #"Please make sure this is a valid URL"
           (public-settings/help-link-custom-destination! "ftp://metabase.com")))

      (is (thrown-with-msg?
           Exception
           #"Please make sure this is a valid URL"
           (public-settings/help-link-custom-destination! "mailto:help@metabase")))))

  (mt/with-premium-features #{}
    (testing "When whitelabeling is not enabled, help-link-custom-destination cannot be set, and always returns its default"
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Setting help-link-custom-destination is not enabled because feature :whitelabel is not available"
           (public-settings/help-link-custom-destination! "http://www.metabase.com")))

      (is (= "https://www.metabase.com/help/premium" (public-settings/help-link-custom-destination))))))

(deftest landing-page-setting-test
  (mt/with-temporary-setting-values [site-url "http://localhost:3000"]
    (testing "should return relative url for valid inputs"
      (public-settings/landing-page! "")
      (is (= "" (public-settings/landing-page)))

      (public-settings/landing-page! "/")
      (is (= "/" (public-settings/landing-page)))

      (public-settings/landing-page! "/one/two/three/")
      (is (= "/one/two/three/" (public-settings/landing-page)))

      (public-settings/landing-page! "no-leading-slash")
      (is (= "/no-leading-slash" (public-settings/landing-page)))

      (public-settings/landing-page! "/pathname?query=param#hash")
      (is (= "/pathname?query=param#hash" (public-settings/landing-page)))

      (public-settings/landing-page! "#hash")
      (is (= "/#hash" (public-settings/landing-page)))

      (with-redefs [public-settings/site-url (constantly "http://localhost")]
        (public-settings/landing-page! "http://localhost/absolute/same-origin")
        (is (= "/absolute/same-origin" (public-settings/landing-page)))))

    (testing "landing-page cannot be set to URLs with external origin"
      (is (thrown-with-msg?
           Exception
           #"This field must be a relative URL."
           (public-settings/landing-page! "https://google.com")))

      (is (thrown-with-msg?
           Exception
           #"This field must be a relative URL."
           (public-settings/landing-page! "sms://?&body=Hello")))

      (is (thrown-with-msg?
           Exception
           #"This field must be a relative URL."
           (public-settings/landing-page! "https://localhost/test")))

      (is (thrown-with-msg?
           Exception
           #"This field must be a relative URL."
           (public-settings/landing-page! "mailto:user@example.com")))

      (is (thrown-with-msg?
           Exception
           #"This field must be a relative URL."
           (public-settings/landing-page! "file:///path/to/resource"))))))

(deftest show-metabase-links-test
  (mt/discard-setting-changes [show-metabase-links]
    (mt/with-premium-features #{:whitelabel}
      (testing "When whitelabeling is enabled, show-metabase-links setting can be set to boolean"
        (public-settings/show-metabase-links! true)
        (is (true? (public-settings/show-metabase-links)))

        (public-settings/show-metabase-links! false)
        (is (= false (public-settings/show-metabase-links)))))

    (mt/with-premium-features #{}
      (testing "When whitelabeling is not enabled, show-metabase-links setting cannot be set, and always returns true"
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Setting show-metabase-links is not enabled because feature :whitelabel is not available"
             (public-settings/show-metabase-links! true)))

        (is (true? (public-settings/show-metabase-links)))))))

(def prevent? #'public-settings/prevent-upgrade?)

(deftest upgrade-threshold-test
  (testing "it is stable but changes across releases"
    (letfn [(threshold [version]
              (with-redefs [config/current-major-version (constantly version)]
                (public-settings/upgrade-threshold)))]
      ;; asserting that across 10 versions we have at leaset 5 distinct values
      (let [thresholds (into [] (map threshold) (range 50 60))]
        ;; kinda the same but very explicit: it's not the same value across versions
        (is (> (count (distinct thresholds)) 1) "value should change between versions")
        (is (< 5 (count (set thresholds))) "value should be decently random between versions")
        (is (every? (fn [x] (and (integer? x) (<= 0 x 100))) thresholds) "should always be an integer between 0 and 100")))))

(deftest prevent-upgrade?-test
  ;; verify that the base value works
  (is (prevent? 45 {:version "0.46" :rollout 50} 75) "base case that it does prevent when rollout is below threshold")
  (testing "never throws and returns truthy"
    (is (not (prevent? 45 {:version "0.46"} 75)) "missing rollout")
    ;; version is weird
    (is (not (prevent? 45 {:version 45} 75)) "version not a version string")
    ;; misshape
    (is (not (prevent? 45 {:latest {:version "0.46" :rollout 80}} 75)) "Wrong shape"))

  (testing "Knows when to upgrade"
    (let [threshold 25
          above     50
          below     15]
      (is (not (prevent? 50 {:version "1.51.23.1" :rollout above} threshold)))
      (is (prevent? 50 {:version "1.51.23.1" :rollout below} threshold))
      (testing "when major is the same, threshold does not matter"
        (is (not (prevent? 50 {:version "1.50.23.1" :rollout above} threshold)) "Same major")
        (is (not (prevent? 50 {:version "1.50.23.1" :rollout below} threshold)) "Same major"))
      (testing "when major is two versions below, follows normal behavior"
        ;; todo: should this offer the next major? ie on 49, 51 is at 10% rollout, should we offer 50 or not?
        (is (not (prevent? 49 {:version "1.51.23.1" :rollout above} threshold)))
        (is (prevent? 49 {:version "1.51.23.1" :rollout below} threshold))))))

(def info #'public-settings/version-info*)

(deftest version-info*-test
  (let [version-info {:latest {:version "1.51.23.1" :rollout 50
                               :highlights ["highlights for 1.51.23.1"]}
                      :older [{:version "1.51.22" :highlights ["highlights for 1.51.22"]}
                              {:version "1.51.21" :highlights ["highlights for 1.51.21"]}]}]
    (testing "When on same major, includes latest"
      (is (= version-info (info version-info {:current-major 51 :upgrade-threshold-value 25}))))
    (testing "When below major"
      (testing "And below rollout threshold lacks latest"
        (is (not (contains? (info version-info {:current-major 50 :upgrade-threshold-value 75}) :latest))))
      (testing "And above rollout threshold includes latest"
        (is (contains? (info version-info {:current-major 50 :upgrade-threshold-value 25}) :latest))))
    (testing "if something feels off, just includes it by default"
      (testing "missing rollout"
        (let [modified (update version-info :latest dissoc :rollout)]
          (is (= modified (info modified {:current-major 51 :upgrade-threshold-value 25})))))
      (testing "version is weird"
        (let [modified (update version-info :latest assoc :version "x01.51")]
          (is (= modified (info modified {:current-major 51 :upgrade-threshold-value 25})))))
      (testing "unknown current threshold"
        (doseq [weird-value [nil "45" (Object.) 23.234 :keyword "string"]]
          (is (= version-info (info version-info {:current-major 51 :upgrade-threshold-value weird-value})))))
      (testing "rollout is a decimal"
        (let [modified (update version-info :latest assoc :rollout 0.2)]
          (is (= modified (info modified {:current-major 51 :upgrade-threshold-value 25}))))))))

(deftest update-channel-test
  (testing "we can set the update channel"
    (mt/discard-setting-changes [update-channel]
      (public-settings/update-channel! "nightly")
      (is (= "nightly" (public-settings/update-channel)))))
  (testing "we can't set the update channel to an invalid value"
    (mt/discard-setting-changes [update-channel]
      (is (thrown?
           IllegalArgumentException
           (public-settings/update-channel! "millennially"))))))

(deftest loading-message-test
  (mt/with-premium-features #{:whitelabel}
    (testing "Loading message can be set by env var"
      (mt/with-temp-env-var-value! [mb-loading-message "running-query"]
        (is (= :running-query (public-settings/loading-message)))))

    (testing "Default value is returned if loading message set via env var to an unsupported keyword value"
      (mt/with-temp-env-var-value! [mb-loading-message "unsupported enum value"]
        (is (= :doing-science (public-settings/loading-message)))))

    (testing "Setter blocks unsupported values set at runtime"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Loading message set to an unsupported value"
                            (public-settings/loading-message! :unsupported-value))))))

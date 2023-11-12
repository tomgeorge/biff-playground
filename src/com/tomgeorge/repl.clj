(ns com.tomgeorge.repl
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [com.biffweb :as biff :refer [q]]
    [com.tomgeorge :as main]
    [portal.api :as p]))


;; This function should only be used from the REPL. Regular application code
;; should receive the system map from the parent Biff component. For example,
;; the use-jetty component merges the system map into incoming Ring requests.
(defn get-context
  []
  (biff/assoc-db @main/system))


(defn add-fixtures
  []
  (biff/submit-tx (get-context)
                  (-> (io/resource "fixtures.edn")
                      slurp
                      edn/read-string)))


(comment

  (def p (p/open))
  (add-tap #'p/submit)
  (p/close)
  ;; Call this function if you make a change to main/initial-system,
  ;; main/components, :tasks, :queues, or config.edn. If you update
  ;; secrets.env, you'll need to restart the app.
  (main/refresh)

  main/system

  ;; Call this in dev if you'd like to add some seed data to your database. If
  ;; you edit the seed data (in resources/fixtures.edn), you can reset the
  ;; database by running `rm -r storage/xtdb` (DON'T run that in prod),
  ;; restarting your app, and calling add-fixtures again.
  (add-fixtures)

  ;; Query the database
  (let [{:keys [biff/db] :as ctx} (get-context)]
    (q db
       '{:find (pull user [*])
         :where [[user :user/email]]}))
  (let [{:keys [biff/handler] :as ctx} (get-context)]
    (handler {:request-method :get
              :uri "/"}))

  ;; Update an existing user's email address
  (let [{:keys [biff/db] :as ctx} (get-context)
        user-id (biff/lookup-id db :user/email "tg82490@gmail.com")]
    (q db
       '{:find (pull msg [*])}))
    

  (sort (keys (get-context)))

  (do
    )

  ;; Check the terminal for output.
  (biff/submit-job (get-context) :echo {:foo "bar"})
  (deref (biff/submit-job-for-result (get-context) :echo {:foo "bar"})))

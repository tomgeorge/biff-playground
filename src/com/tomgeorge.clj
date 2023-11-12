(ns com.tomgeorge
  (:require
    [clojure.test :as test]
    [clojure.tools.logging :as log]
    [clojure.tools.namespace.repl :as tn-repl]
    [com.biffweb :as biff]
    [com.tomgeorge.app :as app]
    [com.tomgeorge.email :as email]
    [com.tomgeorge.home :as home]
    [com.tomgeorge.middleware :as mid]
    [com.tomgeorge.schema :as schema]
    [com.tomgeorge.ui :as ui]
    [com.tomgeorge.worker :as worker]
    [malli.core :as malc]
    [malli.registry :as malr]
    [nrepl.cmdline :as nrepl-cmd]))


(def plugins
  [app/plugin
   (biff/authentication-plugin {})
   home/plugin
   schema/plugin
   worker/plugin])


(def routes
  [["" {:middleware [mid/wrap-site-defaults]}
    (keep :routes plugins)]
   ["" {:middleware [mid/wrap-api-defaults]}
    (keep :api-routes plugins)]])


(def handler
  (-> (biff/reitit-handler {:routes routes})
      mid/wrap-base-defaults))


(def static-pages (apply biff/safe-merge (map :static plugins)))


(defn generate-assets!
  [ctx]
  (biff/export-rum static-pages "target/resources/public")
  (biff/delete-old-files {:dir "target/resources/public"
                          :exts [".html"]}))


(defn on-save
  [ctx]
  (biff/add-libs)
  (biff/eval-files! ctx)
  (generate-assets! ctx)
  (test/run-all-tests #"com.tomgeorge.test.*"))


(def malli-opts
  {:registry (malr/composite-registry
               malc/default-registry
               (apply biff/safe-merge
                      (keep :schema plugins)))})


(def initial-system
  {:biff/plugins #'plugins
   :biff/send-email #'email/send-email
   :biff/handler #'handler
   :biff/malli-opts #'malli-opts
   :biff.beholder/on-save #'on-save
   :biff.middleware/on-error #'ui/on-error
   :biff.xtdb/tx-fns biff/tx-fns
   :com.tomgeorge/chat-clients (atom #{})})


(defonce system (atom {}))


(def components
  [biff/use-config
   biff/use-secrets
   biff/use-xt
   biff/use-queues
   biff/use-tx-listener
   biff/use-jetty
   biff/use-chime
   biff/use-beholder])


(defn start
  []
  (let [new-system (reduce (fn [system component]
                             (log/info "starting:" (str component))
                             (component system))
                           initial-system
                           components)]
    (reset! system new-system)
    (generate-assets! new-system)
    (log/info "System started.")
    (log/info "Go to" (:biff/base-url new-system))))


(defn -main
  [& args]
  (println "called main")
  (start)
  (apply nrepl-cmd/-main args))


(defn refresh
  []
  (doseq [f (:biff/stop @system)]
    (log/info "stopping:" (str f))
    (f))
  (tn-repl/refresh :after `start))

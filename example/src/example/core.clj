(ns example.core
  (:require [dimond.core :as di]
            ;[dime.var :as dv]
            [com.stuartsierra.component :as component]
            [dimond.impl.component :as dmc]
            [dimond.impl.dime :as dmd]
            [org.httpkit.server :as httpkit]
            [ring.middleware.params]
            [clojure.edn :as edn]
            
            [signal.handler :refer [on-signal]]))

(def ^:dynamic *debug* false)
(defn debug [& args] (when *debug* (apply println args)))

(defn ^:expose greeter [name]
  (str "Hello, " name))

(defn ^:expose greeter2 [name]
  (str "Hi, " name))

(defn ^:expose app [^{:inject :greeter} greeter  req]
  (debug "app: " greeter req)
  {:body    (greeter (get-in req [:query-params "name"] "World"))})

(defn with-middleware [app]
  (-> app
      (ring.middleware.params/wrap-params)))

(defn start-server [{:keys [port app stop] :as this}]
  (debug "start-server: " (str "Starting Server on port " port))
  (if stop
    (debug "start-server: Server is already running")
    (assoc this :stop
           (httpkit/run-server (with-middleware app) {:port port}))))

(defn stop-server [{:keys [stop]}]
  (if stop
    (do (debug "stop-server: Stopping server")
        (stop))
    (debug "stop-server: Server is not running.")))


(defn create-system [dime-system port]
  (debug (str "create-system: Creating new system with port " port))
  (assoc dime-system
         :server (component/using
                  (di/create-component {:start #'start-server
                                        :stop  #'stop-server
                                        :port port})
                  {:app :app})))


(def dimond (di/dimond
             ::di/namespaces [*ns*]
             ::di/create-system #'create-system))

(defn -main
  "Example ring server using component"
  [& args]
  (let [port (or (edn/read-string (first args)) 3000)
        dimond (dimond ::di/start port)
        shutdown (fn [sig] (println (str "Caught " (name sig) " signal. Stopping Server")) (dimond ::di/stop))]
    (println (str "Server running on port " port))
    (on-signal :term shutdown)
    (on-signal :int  shutdown)))

;; uncomment in repl to auto refresh if you eval buffer on save
;; but don't leave in uncommented as user namespace is only exists in 
;; dev profile and wont compile in prod configurations
;; (user/dimond ::di/refresh)
;; (user/dimond :app {})
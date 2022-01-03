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

(defn ^:expose greeter [name]
  (str "Hello, " name))

(defn ^:expose greeter2 [name]
  (str "Hi, " name))


(defn ^:expose app [^:inject greeter req]
  (prn greeter req)
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (greeter (get-in req [:query-params "name"] "World"))})

(defn with-middleware [app]
  (-> app
      (ring.middleware.params/wrap-params)))

(defn start-server [{:keys [port app stop] :as this}]
  (println (str "Starting Server on port " port))
  (if stop
    (println "Server is already running" stop)
    (assoc this :stop
           (httpkit/run-server (with-middleware app) {:port port}))))

(defn stop-server [{:keys [stop]}]
  (if stop
    (do (prn "Stopping server")
        (stop))
    (prn "Server is not running.")))

(def dime-system (dmd/build-system [*ns*]))

(defn create-system [port]
  (println (str "Creating new system with port " port))
  (assoc dime-system
         :server (component/using
               (di/create-component {:start #'start-server
                                     :stop  #'stop-server
                                     :port port})
               {:app :app})))

(defonce system nil)

(def dimond (di/dimond
             ::di/var #'system
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


#_(defn -main
    "Example ring server no reloading or di"
    [& args]
    (let [port (or (edn/read-string (first args)) 3000)
          app  (partial app greeter)
          stop-server  (start-server app port)
          shutdown (fn [sig] (println (str "Caught " (name sig) " signal. Stopping Server")) (stop-server))]
      (println (str "Server running on port " port))
      (on-signal :term shutdown)
      (on-signal :int  shutdown)))

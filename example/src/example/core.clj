(ns example.core
  (:require [dimond.core :as di]
            [dime.core :as dm]
            [dime.var :as dv]
            [org.httpkit.server :as httpkit]
            [ring.middleware.params]
            [clojure.edn :as edn]
            [com.stuartsierra.component :as component]
            [signal.handler :refer [on-signal]]))

(defn ^:expose greeter [name]
  (str "Hello, " name))

(defn ^:expose greeter2 [name]
  (str "Hi, " name))


(defn ^:expose app [^:inject greeter req]
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

(defn app-component [greeter]
  (di/create-function-component (partial #'app greeter)))

(defn create-system [port]
  (println (str "creating new system with " port))
  (component/system-map
   :app (app-component greeter)
   :server (component/using
            (di/create-component {:start #'start-server
                                  :stop  #'stop-server
                                  :port port})
            {:app :app})))

(defonce dimond (di/create-dimond #'create-system))

;; swap implementation of greeter without restarting system
(comment
  (swap! (-> dimond meta ::di/system deref :app :f) (constantly (partial app greeter)))
  (swap! (-> dimond meta ::di/system deref :app :f) (constantly (partial app greeter2))))


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

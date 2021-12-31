(ns example.core
  (:require [dimond.core :as di]
            [org.httpkit.server :as httpkit]
            [ring.middleware.params]
            [clojure.edn :as edn]
            [com.stuartsierra.component :as component]
            [signal.handler :refer [on-signal]]))

(defn greeter [name]
  (str "Hello, " name))

(defn app [greeter req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (greeter (get-in req [:query-params "name"] "World"))})

(defn with-middleware [app]
  (-> app
      (ring.middleware.params/wrap-params)))

(defn start-server [app-handler port]
  (httpkit/run-server (with-middleware app-handler) {:port port}))

(defn server-component-start [this app]
  (prn "Starting Server on port 9000")
  (assoc this :stop (start-server (partial app greeter) 9000)))

(defn server-component-stop [this]
  (prn "Stopping server")
  (when-let [stop (:stop this)] (stop)))

(defn start-component [component]
  (prn "staring server")
  (let [state (di/get-injected component)]
    (println "Start " state component)
    component))

(defn stop-component [component]
  (let [state (di/get-injected component)]
    (println "Stop " state component)
    component))


(defn create-system [port]
  (println (str "creating new system with " port) )
  (component/system-map
   :app (partial app greeter)
   :server (component/using
            (di/create-component {:start #'start-component
                                  :stop  #'stop-component
                                  :state {:port port}})
            {:app :app})))

(def dimond (di/create-dimond #'create-system))

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

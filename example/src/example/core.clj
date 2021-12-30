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

(defn start-server [app port]
  (httpkit/run-server (with-middleware app) {:port port}))


(defn -main
  "Example ring server"
  [& args]
  (let [port (or (edn/read-string (first args)) 3000)
        app  (partial app greeter)
        stop-server  (start-server app port)
        shutdown (fn [sig] (println (str "Caught " (name sig) " signal. Stopping Server")) (stop-server))]
    (println (str "Server running on port " port))
    (on-signal :term shutdown)
    (on-signal :int  shutdown)
    ))

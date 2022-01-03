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


;; (defn app-component [greeter]
;;   (dmc/create-function-component (partial #'app greeter)))


;; (defn create-system [port]
;;   (println (str "creating new system with " port))
;;   (component/system-map
;;    :app (app-component greeter)
;;    :server (component/using
;;             (di/create-component {:start #'start-server
;;                                   :stop  #'stop-server
;;                                   :port port})
;;             {:app :app})))

(def dime-system (dmd/build-system [*ns*]))

(defn create-system [port]
  (println (str "creating new system with port " port))
  (assoc dime-system
         :server (component/using
               (di/create-component {:start #'start-server
                                     :stop  #'stop-server
                                     :port port})
               {:app :app})))

(defonce system nil)

;; (defmulti dimond-query (fn [query & args] 
;;                          (println "queried for " query)
;;                          query))
;; (defmethod dimond-query :system [& _] system)
;; (defmethod dimond-query :system-factory [& _] #'create-system)
;; (defmethod dimond-query :create-system [& _]  #'create-system)


;; (def dimond-query
;;   (let [queries {:system (constantly system)
;;                  :create-system (constantly #'create-system)}
;;         default (fn [cmd args] (fn [] (println "warn: " cmd " not found")))]
;;     (fn [query & args]
;;       (println "dimond query" query)
;;       (let [q (get queries query (default query args))]
;;         (apply q args)))))


;; (def dimond-dispatch
;;   (let [events {::di/system-started (fn [system] (alter-var-root #'system (constantly system)))
;;                 ::di/system-stopped (fn [system] (alter-var-root #'system (constantly nil)))}
;;         default (fn [event args] (fn [] (println "no action for  " event " ")))]
;;     (fn [event & args]
;;       (println "dimond event" event)
;;       (let [e (get events event (default event args))]
;;         (apply e args)))))
  
(defmulti dimond-dispatch (fn [event & args]
                         (println "event  " event)
                         event))
(defmethod dimond-dispatch ::di/system-started [event & [system]] 
  (alter-var-root #'system (constantly system)))
(defmethod dimond-dispatch ::di/system-stopped [event & [system]]
  (alter-var-root #'system (constantly nil)))

(defmethod dimond-dispatch :default [event & args] 
  (println "no handler for " event args))

(def dimond (di/dimond
             ::di/var #'system
             ::di/create-system #'create-system
            ;;  ::di/dimond-query (partial #'dimond-var-query #'system) 
            ;;  ::di/dimond-dispatch #'dimond-dispatch
             ))

;; swap implementation of greeter without restarting system
(comment
  (swap! (-> dimond meta ::di/system deref :app :f) (constantly (partial app greeter)))
  (swap! (-> dimond meta ::di/system deref :app :f) (constantly (partial app greeter2)))
  ;
  )


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

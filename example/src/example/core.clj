(ns example.core
  (:require [dimond.core :as di]
            [dime.core :as dm]
            [dime.var :as dv]
            [org.httpkit.server :as httpkit]
            [ring.middleware.params]
            [clojure.edn :as edn]
            [com.stuartsierra.component :as component]
            [com.stuartsierra.dependency :as dep]
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


;; (defn app-component [greeter]
;;   (di/create-injectable-function (partial #'app greeter)))


(defn start-injectable-function [this]
  (let [dependency-ids (get this ::ordered-dependency-ids)
        the-var (get this ::var)
        dependencies (map #(get this %) dependency-ids)
        f (get this :f)]
    (reset! f (partial the-var dependencies)))
  this)

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

(defn create-dime-component [id the-var ordered-dependency-ids]
  (dimond.impl.component/create-function-component
   (fn [] (throw "System not Started"))
   {:start #'start-injectable-function
    ::id id
    ::var the-var
    ::ordered-dependency-ids (vec ordered-dependency-ids)}))

(defn create-dime-components
  ([dime-graph]
   (create-dime-components
    (keys dime-graph) dime-graph (dime.core/attr-map dime-graph :dep-ids)))
  ([ids get-var get-deps]
   (zipmap ids
           (->> ids
                (mapv (juxt identity get-var get-deps))
                (map #(apply create-dime-component %))))))

(defn build-system [ids get-component get-deps]
  (zipmap ids
          (for [id ids
                :let [component (get-component id)]]
            (if-let [deps (seq (get-deps id))]
              (component/using component (vec deps))
              component))))
    

(comment 
  

  (def dime-graph (did/scan-nsvar-metadata [*ns*]))
  (def get-deps (dime.core/attr-map dime-graph :dep-ids))
  (def ids (keys dime-graph))
  (def dime-comp (create-dime-components  ids dime-graph get-deps))
  (def system (build-system ids dime-comp get-deps))
  )




(comment
  (require '[dimond.impl.dime :as did])
  (def dg (-> (did/scan-nsvar-metadata [*ns*])
              (did/build-dependency-graph-from-dime)))

  (def dc (-> (did/scan-nsvar-metadata [*ns*]))))

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

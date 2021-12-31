(ns dimond.core
  (:require [dimond.impl.component :as dc]
            [com.stuartsierra.component :as component]))

(defn create-component
  "Create a lifecyle method that will call the specified 
   start and stop methods.  

   If you pass a var (#'my-start-method) any changes made by 
   repl session will be applied without recreating 
   the component (and thus restarting the system).

   If you pass only a function the component must be recreated
   to apply any changes to the start stop methods."
  [{:keys [start stop] :as opts}]
  (or (and start (var? start))
      (println "start should be a var to enable reloading"))
  (or (and stop (var? stop))
      (println "stop should be a var to enable reloading"))
  (with-meta
    (dc/map->InjectableComponent (dissoc opts :start :stop))
    {::dc/start start ::dc/stop stop}))

(defn create-function-component 
  ([] (create-function-component (constantly nil)))
  ([f]
   (dc/->InjectableFunctionComponent (atom f))))

;;(def dimond-action nil)
(defmulti dimond-action (fn [dimond action & args] action))

(defn factory [dimond]
  (-> dimond meta ::system-factory))

(defn system [dimond]
  @(-> dimond meta ::system))

(defmethod dimond-action ::start [dimond _action & [args]]
  (prn "starting" args)

  (let [system-map (apply (factory dimond) args)]
    (reset! (-> dimond meta ::system) (component/start-system system-map))))

(defmethod dimond-action ::stop [dimond _action & _args]
  (swap! (-> dimond meta ::system) component/stop-system))

(defmethod  dimond-action :default [dimond action & [args]] 
  (println "no action registered" action))

(defn create-dimond [system-factory]
  (assert (ifn? system-factory) "system-factory required")
  (when-not (var? system-factory)
    (println "Use var to support repl reloading"))
  (let [m {::system-factory system-factory
           ::system (atom nil)}]
    (letfn [(dimond [action & args]
            (dimond-action (with-meta dimond m) action args))]
    (with-meta dimond m))))
  
  

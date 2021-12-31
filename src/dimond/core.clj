(ns dimond.core
  (:require [dimond.impl.component :as dc]
            [dimond.impl.dime :as dm]
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





(defn factory [dimond]
  (-> dimond meta ::system-factory))

(defn mutable-system [dimond]
  (-> dimond meta ::system))

(defn system [dimond]
  @(-> dimond meta ::system))

(defmulti system-action (fn [system action & args] action))

(defn uget [map key]
  (let [value (get map key ::not-found)]
    (if (and (= value ::not-found) (not (qualified-keyword? key)))
      (if-let [matches (get (group-by name (keys map)) (name key) ::not-found)]
        (if (= (count matches) 1)
          (get map (first matches) ::not-found)
          ::ambiguous))
      value)))

(defmethod system-action :default [system action & args]
  (if (fn? action)
    (apply (partial action system) args)
    (if-let [value (uget system action)]
      (if (ifn? value)
        (apply value args)
        value)
      (println (str "Don't know what to do with " action)))))


;;(def dimond-action nil)
(defmulti dimond-action (fn [dimond action & args] action))

(defmethod dimond-action ::start [dimond _action & [args]]
  (prn "starting" args)

  (let [system-map (apply (factory dimond) args)]
    (reset! (mutable-system dimond) (component/start-system system-map))))

(defmethod dimond-action ::stop [dimond _action & _args]
  (swap! (mutable-system dimond) component/stop-system))

(defmethod  dimond-action :default [dimond action & [args]] 
  (if-let [sys (system dimond)]
    (apply system-action sys action args)
    (println "System is not running")
    )
  )


(defn create-dimond [system-factory]
  (assert (ifn? system-factory) "system-factory required")
  (when-not (var? system-factory)
    (println "Use var to support repl reloading"))
  (let [m {::system-factory system-factory
           ::system (atom nil)}]
    (letfn [(dimond [action & args]
            (dimond-action (with-meta dimond m) action args))]
    (with-meta dimond m))))


(defn dimond-dime [namespaces]
  (let [system-factory #(dm/build-system namespaces)]
    (create-dimond (with-meta system-factory {}))))
(ns dimond.core
  (:require [dimond.impl.component :as dc]
            [com.stuartsierra.component :as component]))


(def create-component
  "Create a lifecyle method that will call the specified 
   start and stop methods.  

   If you pass a var (#'my-start-method) any changes made by 
   repl session will be applied without recreating 
   the component (and thus restarting the system).

   If you pass only a function the component must be recreated
   to apply any changes to the start stop methods."
  dc/create-component)



(defn create-function-component
  "IFn implementation for partial application that
   allows the underlying function to be reset.
   This allows for the fuction to be replaced 
   when its injected dependencies haved changed"
  ([] (create-function-component (constantly nil)))
  ([f & [opts]]
   (prn f opts)
   (let [f (if (not (instance? clojure.lang.Atom f)) (atom f) f)
         {:keys [start stop] :as opts} (or opts {})
         props (dissoc opts :start :stop)]
     (with-meta (dc/map->InjectableFunctionComponent (assoc props :f f))
       {::dc/start start ::dc/stop stop}))))

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
  
  

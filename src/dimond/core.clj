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
  ((::dimond-query dimond) :create-system))

(defn system [dimond]
  ((::dimond-query dimond) :system))

(defn dimond-dispatch [dimond event & args]
  (apply (partial (::dimond-dispatch dimond) event) args))


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
  ;;(prn "action" action args system)
  (if (fn? action)
    (apply (partial action system) args)
    (if-let [value (uget system action)]
      (if (ifn? value)
        (apply value args)
        value)
      (println (str "Don't know what to do with " action)))))


;;(def dimond-action nil)
(defmulti dimond-action (fn [dimond action & args] action))

(defmethod dimond-action ::start [dimond _action & args]
  (dimond-dispatch dimond ::system-starting)
  (let [system (system dimond)]
    (if system
      (dimond-dispatch dimond ::already-started)
      (let [create-system (factory dimond)
            system (apply create-system args)
            _ (dimond-dispatch dimond ::system-created system)
            system  (component/start-system system)
            _ (dimond-dispatch dimond ::system-started system)
            ]
        nil))))

(defmethod dimond-action ::stop [dimond _action & _args]
  (dimond-dispatch dimond ::system-stopping)
  (let [system (system dimond)
        system (when system (component/stop-system system))]
    (dimond-dispatch dimond ::system-stopped system)))

(defmethod  dimond-action :default [dimond action & args]
  (if-let [sys (system dimond)]
    (apply (partial system-action sys action) args)
    (dimond-dispatch dimond action args)))

(defn create-dimond [dimond-query dimond-dispatch]
  (let [the-dimond {::dimond-query dimond-query
                    ::dimond-dispatch dimond-dispatch}]
    (fn [event & args]
      ;;(println "dimond event" event)
      (apply (partial #'dimond-action the-dimond event) args))))
  


;; (defn dimond-dime [namespaces]
;;   (let [system-factory #(dm/build-system namespaces)]
;;     (create-dimond (with-meta system-factory {}))))
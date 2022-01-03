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

(def debug (constantly nil))
;(def debug println)



(defn factory [dimond]
  ((-> dimond meta ::dimond-query) :create-system))

(defn system [dimond]
  ((-> dimond meta ::dimond-query) :system))

(defn dimond-dispatch [dimond event & args]
  (apply (partial (-> dimond meta ::dimond-dispatch) event) args))

(def system-action nil)
(defmulti system-action
  (fn [system action & args]
    (debug "dispatching system-action " action)
    action))

(defn uget [map key]
  (let [value (get map key ::not-found)]
    (if (and (= value ::not-found) (not (qualified-keyword? key)))
      (if-let [matches (get (group-by name (keys map)) (name key) ::not-found)]
        (if (= (count matches) 1)
          (get map (first matches) ::not-found)
          ::ambiguous))
      value)))

(defmethod system-action :default [system action & args]
  (debug "system-action :default" action args system)
  (if (fn? action)
    (apply (partial action system) args)
    (if-let [value (uget system action)]
      (if (ifn? value)
        (apply value args)
        value)
      (println (str "Don't know what to do with " action)))))


(def dimond-action nil)
(defmulti dimond-action
  (fn [dimond action & args]
    (debug "dispatching dimond-action " action)
    action))

(defmethod dimond-action ::start [dimond _action & args]
  (dimond-dispatch dimond ::system-starting)
  (let [system (system dimond)]
    (if system
      (do (dimond-dispatch dimond ::already-started)
          dimond)
      (let [create-system (factory dimond)
            system (apply create-system args)
            _ (dimond-dispatch dimond ::system-created system)
            system  (component/start-system system)
            _ (dimond-dispatch dimond ::system-started system)]
        dimond))))

(defmethod dimond-action ::stop [dimond _action & _args]
  (dimond-dispatch dimond ::system-stopping)
  (let [system (system dimond)
        system (when system (component/stop-system system))]
    (dimond-dispatch dimond ::system-stopped system))
  dimond)

(defmethod  dimond-action :default [dimond action & args]
  (if-let [sys (system dimond)]
    (apply (partial system-action sys action) args)
    (dimond-dispatch dimond action args)))

(def dimond-var-query nil) 
(defmulti dimond-var-query
  (fn [var create-system query & args]
    (debug "dimond-var-query query for " var query)
    query))
(defmethod dimond-var-query :system [var create-system & _]
  (deref var))
(defmethod dimond-var-query :system-factory [var create-system & _] create-system)
(defmethod dimond-var-query :create-system [var create-system & _]  create-system)

(def dimond-var-dispatch nil)
(defmulti dimond-var-dispatch
  (fn [var event & args]
    (debug "dimond-var-dispatch for " event)
    event))

(defmethod dimond-var-dispatch ::system-started [var event & [system]]
  (alter-var-root var (constantly system)))
(defmethod dimond-var-dispatch ::system-stopped [var event & [system]]
  (alter-var-root var (constantly nil)))
(defmethod dimond-var-dispatch :default [var event & _]
  (debug "no handler for " event))

(defn var-storage [var create-system]
  (assert (var? var) "var required")
  (assert (ifn? create-system) "create system required")
  {::dimond-query (partial #'dimond-var-query var create-system)
   ::dimond-dispatch (partial #'dimond-var-dispatch var)})


(defmulti dimond-atom-query
  (fn [atom create-system query & args]
    (debug "dimond-atom-query query for " query)
    query))

(defmethod dimond-atom-query :system [atom create-system & _] @atom)
(defmethod dimond-atom-query :system-factory [atom create-system & _] create-system)
(defmethod dimond-atom-query :create-system [atom create-system & _]  create-system)

(defmulti dimond-atom-dispatch
  (fn [atom event & args]
    (debug "dimond-atom-dispatch " event)
    event))

(defmethod dimond-atom-dispatch ::system-started [atom event & [system]]
  (reset! atom system))
(defmethod dimond-atom-dispatch ::system-stopped [atom event & [system]]
  (reset! atom nil))
(defmethod dimond-atom-dispatch :default [var event & _]
  (debug "no handler for " event))


(defn var-storage [var create-system]
  (assert (var? var) "var required")
  (assert (ifn? create-system) "create system required")
  {::dimond-query (partial #'dimond-var-query var create-system)
   ::dimond-dispatch (partial #'dimond-var-dispatch var)})

(defn atom-storage [atom create-system]
  (assert (instance? clojure.lang.Atom atom) "atom required")
  (assert (ifn? create-system) "create system required")

  {::dimond-query (partial #'dimond-atom-query atom create-system)
   ::dimond-dispatch (partial #'dimond-atom-dispatch atom)})



(defn dimond [& args]
  (let [;; the-dimond {::dimond-query dimond-query
        ;;             ::dimond-dispatch dimond-dispatch}

        argmap (apply hash-map args)
        {::keys [var atom create-system]} argmap

        _ (assert (not (and (contains? argmap ::var) (nil? var)))
                  "Must specify #'var not var")

        dimond-state
        (if (and var create-system)
          (merge argmap (var-storage var create-system))
          argmap)

        dimond-state
        (if (and atom create-system)
          (merge dimond-state (atom-storage atom create-system))
          dimond-state)
        
        _ (assert (contains? dimond-state ::dimond-query) (str "must have " ::dimond-query))
        _ (assert (contains? dimond-state ::dimond-dispatch) (str "must have " ::dimond-dispatch))
        ]    
    (letfn [(the-dimond [event & args]
              (apply (partial #'dimond-action (with-meta the-dimond dimond-state) event) args))]
      (with-meta the-dimond dimond-state))))



;; (defn dimond-dime [namespaces]
;;   (let [system-factory #(dm/build-system namespaces)]
;;     (create-dimond (with-meta system-factory {}))))
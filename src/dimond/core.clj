(ns dimond.core
  (:require [dimond.impl.component :as dc]
            [dimond.impl.dime :as dm]
            [com.stuartsierra.component :as component]))

(def ^:dynamic *debug* false)
(defn debug [& args] (when *debug* (apply (partial println (str *ns* ":") ) args)))


(defn create-component
  "Create a lifecyle method that will call the specified 
   start and stop methods."
  [{:keys [start stop] :as opts}]
  (or (and start (var? start))
      (println "start should be a var to enable reloading"))
  (or (and stop (var? stop))
      (println "stop should be a var to enable reloading"))
  (with-meta
    (dc/map->InjectableComponent (dissoc opts :start :stop))
    {::dc/start start ::dc/stop stop}))

(defn factory [dimond]
  ((-> dimond meta ::dimond-query) :create-system))

(defn system [dimond]
  ((-> dimond meta ::dimond-query) :system))

;; todo: although intended for side-effecting events it may make sense if 
;; all handlers still return the dimond so that var/atom store is only
;; needed for repl and not needed if you are threading operations in a main 
;; method where dimond can be chained
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

(defmethod dimond-action ::refresh [dimond _action & _args]
  (dimond-dispatch dimond ::system-refreshing)
  (when-let [system (system dimond)]
    (let [system (dm/refresh-dependencies system)]
      (dimond-dispatch dimond ::system-refreshed system)))
  dimond)

(defmethod  dimond-action :default [dimond action & args]
  (if-let [sys (system dimond)]
    (apply (partial system-action sys action) args)
    (dimond-dispatch dimond action args)))

(def dimond-var-query nil) 
(defmulti dimond-var-query
  (fn [var create-system query & args]
    (debug "dimond-var-query query for " query)
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
(defmethod dimond-var-dispatch ::system-refreshed [var event & [system]]
  (alter-var-root var (constantly system)))
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
(defmethod dimond-atom-dispatch ::system-refreshed [var event & [system]]
  (debug "refreshed" system)
  (reset! var (constantly system)))
(defmethod dimond-atom-dispatch :default [var event & _]
  (debug "no handler for " event))

;; todo: create-system indpendent of var/atom storage
;; refactor a bit
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

(defn build-system [namespaces]
  (let [dime (dm/scan-namespaces namespaces)]
    (zipmap (keys dime) (map dm/create-var-component (vals dime)))))

(defn dimond [& args]
  (let [;; the-dimond {::dimond-query dimond-query
        ;;             ::dimond-dispatch dimond-dispatch}

        argmap (apply hash-map args)
        {the-var ::var the-atom ::atom} argmap
        {::keys [create-system namespaces]} argmap


        ;; create-system is optional if namespaces are specified
        ;; as one will be created by scanning namespaces with 
        ;; dime.  If both are specified the auto generated system
        ;; will be passed to create-system so that additional 
        ;; components can be added if desired
        create-system (if namespaces
                        (fn [& args] (apply (partial (or create-system identity) (build-system namespaces)) args))
                        create-system)

        _ (assert create-system "do not know how to build system, specify create-system and/or namespaces")

        dimond-store
        (if the-var
          (var-storage the-var create-system)
          (atom-storage (or the-atom (atom nil)) create-system))
        

        dimond-state (merge argmap  dimond-store)
        
        _ (assert (contains? dimond-state ::dimond-query) (str "must have " ::dimond-query))
        _ (assert (contains? dimond-state ::dimond-dispatch) (str "must have " ::dimond-dispatch))]    
    (letfn [(the-dimond [event & args]
              (apply (partial #'dimond-action (with-meta the-dimond dimond-state) event) args))]
      (with-meta the-dimond dimond-state))))



(ns dimond.impl.dime
  (:require
   ;[com.stuartsierra.dependency :as dep]
   [com.stuartsierra.component :as component]
   [dime.var :as dv]
   [dime.type :as dt]))

(def debug (constantly nil))
;;(def debug println)
(def warn println)

(defn scan-namespaces [namespaces]
  (-> (mapv #(if (instance? clojure.lang.Namespace %) (-> % str symbol) %)
            namespaces)
      (dv/ns-vars->graph)))

(defn get-var-dep-ids [the-var]
  (-> the-var dt/iattrs :dep-ids))

(defn get-var [var-component]
  (-> var-component meta ::var))

(defn get-cached-var-value [var-component]
  (-> var-component meta ::value))

(defn get-component-dep-ids [var-component]
  (-> var-component meta ::dep-ids))

;; if the var has changed but the dependencies are still the same
;; we can use the updated function
;; However, if the user has changed the dependency list then component
;; must first be updated with the new dependencies.  If this is the case
;; emit a warning and use the previous function value
(defn get-usable-function [var-component]
  (let [the-var (get-var var-component)]
    (if (or (identical? the-var (get-cached-var-value var-component))
            (= (get-var-dep-ids the-var) (get-component-dep-ids  var-component)))
      the-var
      ;; todo: dispatch event instead?
      (do (warn the-var " dependency list has changed.  system refresh required to use new function")
          (get-cached-var-value var-component)))))

(defn get-partial-args [var-component]
  (if-let [dep-ids (seq (get-component-dep-ids var-component))]
    ((apply juxt dep-ids) var-component)))

(defn gen-nonvariadic-invokes []
  (for [arity (range 0 21)
        :let [args (repeatedly arity gensym)]]
    `(~'invoke [this# ~@args] ((partial (apply partial (get-usable-function this#) (get-partial-args this#)) ~@args)))))

(defn gen-variadic-invoke []
  (let [args (repeatedly 21 gensym)]
    `(~'invoke [this# ~@args] (apply (apply partial (get-usable-function this#) (get-partial-args this#)) ~@args))))

(defn gen-apply-to []
  `(~'applyTo [this# args#] (apply (apply partial (get-usable-function this#) (get-partial-args this#)) args#)))

(defn extend-IFn []
  `(clojure.lang.IFn
    ~@(gen-nonvariadic-invokes)
    ~(gen-variadic-invoke)
    ~(gen-apply-to)))

(defmacro def-var-component []
  `(defrecord ~'VarComponent []
     ~@(extend-IFn)))

(def-var-component)


(defn create-var-component* [the-var]
  (with-meta (->VarComponent) {::var the-var}))

(defn is-var-component? [component]
  (not (nil? (::var (meta component)))))

(defn update-component-dependency-meta [component]
  (if (is-var-component? component)
    (let [the-var (get-var component)
          dep-ids (get-var-dep-ids the-var)]
      (-> component
          (vary-meta dissoc ::component/dependencies)
          (vary-meta assoc ::value (deref the-var) ::dep-ids dep-ids)
          (component/using
           (vec (get-var-dep-ids (get-var component))))))
    component))

(defn create-var-component [the-var]
  (-> the-var create-var-component* update-component-dependency-meta))

(comment
  (defn ^:expose testing [^:inject x & args] (vec (cons x args)))
  (def tp (assoc (create-var-component #'testing) :x 0))
  (tp) ;; => [0]
  (tp 1) ;; => [0 1]
  (tp 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24) ;; => [0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24]
  (apply tp (range 1 25)) ;; => [0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24]
  )

(defn refresh-dependencies [system]
  (let [component-keys (keys system)
        system         (zipmap component-keys
                               (mapv update-component-dependency-meta (vals system)))
        ; updating with identity reinjects depenedencies 
        ; todo: trigger an event to allow components to optionally 
        ; restart?
        ; todo: also refreshing dependencies from var wuold overwrite
        ; any intentional remappings of dependencies that may have been
        ; applied in create-system.  Perhaps need to move the dependency
        ; remapping out of create system to a different user supplied 
        ; function? given refresh is only intended for repl development
        ; perhaps its fine if var is used as source of truth in dev
        ; and create-system can be more explicit for prod configs
        ; but could be confusing if system starts one way then changes
        ; on first refresh, perhaps a refresh after create but that 
        ; could also be confusing
        system (component/update-system system component-keys identity)]
    system))
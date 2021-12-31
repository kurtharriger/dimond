(ns dimond.impl.component
  (:require [com.stuartsierra.component :as component]))

;; reused in both InjectableComponent and InjectableFunctionComponet
(defn extend-lifecycle 
  "Note: defrecord protocol implementation spans multiple forms:
   
   proto
   (start ...)
   (stop ...)
   Not:
   (proto (start ...) (stop ...))

   Thus you must use ~@ to insert in macro
   "
  []
  `[component/Lifecycle
    (~'start [this#] (when-let [start# (-> this# meta ::start)] (start# this#)))
    (~'stop [this#] (when-let [stop# (-> this# meta ::stop)] (stop# this#)))])

(defmacro def-injectable-component []
  `(defrecord ~'InjectableComponent []
    ~@(extend-lifecycle)))

(def-injectable-component)

(defn gen-nonvariadic-invokes []
  (for [arity (range 0 21)
        :let [args (repeatedly arity gensym)]]
    `(~'invoke [this# ~@args] (~'@f ~@args))))

(defn gen-variadic-invoke []
  (let [args (repeatedly 21 gensym)]
    `(~'invoke [this# ~@args] (apply ~'@f ~@args))))

(defn gen-apply-to []
  `(~'applyTo [this# args#] (apply ~'@f args#)))

(defn extend-IFn []
  `(clojure.lang.IFn
    ~@(gen-nonvariadic-invokes)
    ~(gen-variadic-invoke)
    ~(gen-apply-to)))

(defmacro def-partial-function-component []
  `(defrecord ~'InjectableFunctionComponent [~'f]
     ~@(extend-IFn)
     ~@(extend-lifecycle)))

(def-partial-function-component)

(comment
  (def pf (->InjectableFunctionComponent (atom vector)))
  (pf) ;; => []
  (pf 1 2 3) ;; => [1 2 3]
  (pf 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 3) ;; => [1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 3]
  (apply pf [1 2 3 4]) ;; => [1 2 3 4]

  (def pf2 (reset! (:f pf) (partial vector 0)))
  (pf2) ;; => [0]
  (pf2 1 2 3) ;; => [0 1 2 3]
  (apply pf2 [1 2 3]) ;; => [0 1 2 3]
  ;
  )

(defn create-component
  [{:keys [start stop] :as opts}]
  (or (and start (var? start))
      (println "start should be a var to enable reloading"))
  (or (and stop (var? stop))
      (println "stop should be a var to enable reloading"))
  (with-meta
    (map->InjectableComponent (dissoc opts :start :stop))
    {::start start ::stop stop}))


(ns dimond.impl.component
  (:require [com.stuartsierra.component :as component]))

(defrecord InjectableComponent [start stop]
  component/Lifecycle
  (start [this] (when-let [start (-> this meta ::start)] (start this)))
  (stop [this] (when-let [stop (-> this meta ::stop)] (stop this))))

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

(defmacro def-partial-function-component
  "IFn implementation for partial application that
   allows the underlying function to be reset.
   This allows for the fuction to be replaced 
   when its injected dependencies haved changed"
  []
  `(defrecord ~'InjectableFunctionComponent [~'f]
     ~@(extend-IFn)))

(def-partial-function-component)

(comment
  (def pf (->InjectableFunctionComponent (atom vector)))
  (pf) ;; => []
  (pf 1 2 3) ;; => [1 2 3]
  (pf 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 3) ;; => [1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 3]
  (apply pf [1 2 3 4]) ;; => [1 2 3 4]

  (def p2 (reset! (:f pf) (partial vector 0)))
  (pf2) ;; => [0]
  (pf2 1 2 3) ;; => [0 1 2 3]
  (apply pf2 [1 2 3]) ;; => [0 1 2 3]
  ;
  )

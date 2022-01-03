(ns dimond.impl.dime
  (:require
   ;[com.stuartsierra.dependency :as dep]
   [com.stuartsierra.component :as component]
   [dimond.impl.component :as dmc]
   [dime.var :as dv]
   [dime.type :as dt]
   [dime.core :as dc]))



(def debug (constantly nil))
(def debug println)

(defn scan-namespaces [namespaces]
  (-> (mapv #(if (instance? clojure.lang.Namespace %) (-> % str symbol) %)
            namespaces)
      (dv/ns-vars->graph)))


;; (defn start-injectable-function! [this]
;;   (let [dependency-ids (get this ::ordered-dependency-ids)
;;         the-var (get this ::var)
;;         dependencies (map #(get this %) dependency-ids)
;;         f (get this :f)]
;;     (reset! f (apply partial the-var dependencies)))
;;   this)

;; (defn create-dime-component [id the-var ordered-dependency-ids]
;;   (dmc/create-function-component
;;    (fn [] (throw "System not Started"))
;;    {:start #'start-injectable-function!
;;     ::id id
;;     ::var the-var
;;     ::ordered-dependency-ids (vec ordered-dependency-ids)}))



;; (defn create-dime-component [the-var]
;;   (assert (var? the-var) "expecting a var")
;;   (let [dependency-ids (select-)])
;;   (partial (deref the-var) (select-keys ))
;;   )


;; (defn create-dime-components
;;   [ids get-var get-deps]
;;   (zipmap ids
;;           (->> ids
;;                (mapv (juxt identity get-var get-deps))
;;                (map #(apply create-dime-component %)))))

;; (defn build-system* [ids get-component get-deps]
;;   (zipmap ids
;;           (for [id ids
;;                 :let [component (get-component id)]]
;;             (if-let [deps (seq (get-deps id))]
;;               (component/using component (vec deps))
;;               component))))

;; (defn build-system [namespaces]
;;   (let [dime-map (scan-namespaces namespaces)
;;         ids (keys dime-map)
;;         deps-map (dime.core/attr-map dime-map :dep-ids)
;;         component-map (create-dime-components ids dime-map deps-map)]
;;     (build-system* ids component-map deps-map)))

;; (defn- add-to-graph [graph [component-id dependency-ids]]
;;   (reduce (fn [graph dependency-id]
;;             (dep/depend graph component-id dependency-id))
;;           graph dependency-ids))

;; (defn build-dependency-graph-from-dime [dime-dep-graph]
;;   (reduce add-to-graph (dep/graph) (dc/attr-map dime-dep-graph :dep-ids)))



(defn get-var [var-component]
  (-> var-component meta ::var))

(defn get-dep-ids [the-var]
  (seq (-> the-var dt/iattrs :dep-ids)))

;; if var signature changes this will pull the new dependency without system restart
;; however component will not resolve and inject the dependencies into the component
;; until component is restarted so using the start component may have been better
;; also need to rebuild the wrapper (component/using)
(defn get-partial-args [var-component]
  (if-let [dep-ids (-> var-component get-var get-dep-ids )]
    ((apply juxt dep-ids) var-component)))

(defn gen-nonvariadic-invokes []
  (for [arity (range 0 21)
        :let [args (repeatedly arity gensym)]]
                             ;((partial (apply partial testing          [0 2])                     1))
    `(~'invoke [this# ~@args] ((partial (apply partial (get-var this#) (get-partial-args this#)) ~@args)))))

(defn gen-variadic-invoke []
  (let [args (repeatedly 21 gensym)]
    `(~'invoke [this# ~@args] (apply (apply partial (get-var this#) (get-partial-args this#)) ~@args))))

(defn gen-apply-to []
  `(~'applyTo [this# args#] (apply (apply partial (get-var this#) (get-partial-args this#)) args#)))

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

(defn create-var-component [the-var]
  (let [component (create-var-component* the-var)]
    (component/using component (vec (get-dep-ids the-var)))))


(comment
  (defn ^:expose testing [^:inject x & args] (vec (cons x args)))
  (def tp (assoc (create-var-component #'testing) :x 0))
  (tp) ;; => [0]
  (tp 1) ;; => [0 1]
  (tp 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24) ;; => [0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24]
  (apply tp (range 1 25)) ;; => [0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24]
  )

(defn build-system [namespaces] 
  (let [dime (scan-namespaces namespaces)]
    (zipmap (keys dime) (map create-var-component (vals dime)))))
(ns dimond.impl.dime
  (:require
   ;[com.stuartsierra.dependency :as dep]
   [com.stuartsierra.component :as component]
   [dimond.impl.component :as dmc]
   [dime.var :as dv]
   [dime.core :as dc]))


(defn scan-namespaces [namespaces]
  (-> (mapv #(if (instance? clojure.lang.Namespace %) (-> % str symbol) %)
            namespaces)
      (dv/ns-vars->graph)))


(defn start-injectable-function! [this]
  (let [dependency-ids (get this ::ordered-dependency-ids)
        the-var (get this ::var)
        dependencies (map #(get this %) dependency-ids)
        f (get this :f)]
    (reset! f (apply partial the-var dependencies)))
  this)

(defn create-dime-component [id the-var ordered-dependency-ids]
  (dmc/create-function-component
   (fn [] (throw "System not Started"))
   {:start #'start-injectable-function!
    ::id id
    ::var the-var
    ::ordered-dependency-ids (vec ordered-dependency-ids)}))

(defn create-dime-components
  [ids get-var get-deps]
   (zipmap ids
           (->> ids
                (mapv (juxt identity get-var get-deps))
                (map #(apply create-dime-component %)))))

(defn build-system* [ids get-component get-deps]
  (zipmap ids
          (for [id ids
                :let [component (get-component id)]]
            (if-let [deps (seq (get-deps id))]
              (component/using component (vec deps))
              component))))

(defn build-system [namespaces]
  (let [dime-map (scan-namespaces namespaces)
        ids (keys dime-map)
        deps-map (dime.core/attr-map dime-map :dep-ids)
        component-map (create-dime-components ids dime-map deps-map)]
    (build-system* ids component-map deps-map)))

;; (defn- add-to-graph [graph [component-id dependency-ids]]
;;   (reduce (fn [graph dependency-id]
;;             (dep/depend graph component-id dependency-id))
;;           graph dependency-ids))

;; (defn build-dependency-graph-from-dime [dime-dep-graph]
;;   (reduce add-to-graph (dep/graph) (dc/attr-map dime-dep-graph :dep-ids)))

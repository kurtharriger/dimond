(ns dimond.impl.dime
  (:require
   [com.stuartsierra.dependency :as dep]
   [dime.var :as dv]
   [dime.core :as dc]))


(defn- add-to-graph [graph [component-id dependency-ids]]
  (reduce (fn [graph dependency-id]
            (dep/depend graph component-id dependency-id))
          graph dependency-ids))

(defn scan-nsvar-metadata [namespaces]
  (-> (mapv #(if (instance? clojure.lang.Namespace %) (-> % str symbol) %)
            namespaces)
      (dv/ns-vars->graph)
      ))

(defn build-dependency-graph-from-dime [dime-dep-graph]
  (reduce add-to-graph (dep/graph) (dc/attr-map dime-dep-graph :dep-ids)))

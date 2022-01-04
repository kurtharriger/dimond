(ns user
  (:require [dimond.core :as di]
            [example.core :as ec]
            [clojure.tools.namespace.repl :as repl ]
            ))

(defonce system nil)

(def dimond (di/dimond
             ::di/var #'system
             ::di/namespaces ['example.core]
             ::di/create-system #'ec/create-system))

(defn refresh-dimond []
  (dimond ::di/refresh))


(defn refresh [] 
  (repl/refresh-all :after 'user/refresh-dimond))

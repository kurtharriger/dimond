(ns user
  (:require [dimond.core :as di]
            [example.core :as ec]))

(defonce system nil)

(def dimond (di/dimond
             ::di/var #'system
             ::di/namespaces ['example.core]
             ::di/create-system #'ec/create-system))

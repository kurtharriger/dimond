(ns example.core
  (:require [dimond.core :as di]))

(defn -main
  "I don't do a whole lot."
  [& [args]]
  (di/foo "world")
  (println "Hello, World!"))

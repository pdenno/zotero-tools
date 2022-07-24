(ns zotero-tools.script
  (:require
   [taoensso.timbre :as log]
   ["ansiparse" :as ap ]
   ["zotero-api-client" :as zapi]))

(def data {:key :value})

(add-tap #(println %))

(defn parse-ansi-string [s]
  (js/ansiparse s))

(defn main [& _cli-args]
  (println (str "ANSIparse of 'foo' = " (ap/ansiparse.)))
  (log/info "Starting Client"))

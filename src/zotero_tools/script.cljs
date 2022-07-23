(ns zotero-tools.script
  (:require
   [taoensso.timbre :as log]
   ["zotero-api-client"]))

(defn main [& _cli-args]
  (prn "Hello world, again!")
  (log/info "Starting Client"))

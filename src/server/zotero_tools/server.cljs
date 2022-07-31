(ns zotero-tools.server
  (:require
   [cljs.core.async :refer [go]]
   [cljs.core.async.interop :refer-macros [<p!]] ; <p! takes the value of a js promise resolution.
   [taoensso.timbre :as log]
   [datascript.core :as d]
   ["http" :as http]
   ["zotero-api-client$default" :as zotero] ; https://shadow-cljs.github.io/docs/UsersGuide.html#js-deps
   [zotero-tools.util :as util]))

(defn request-handler [_req res]
  (.end res "This is printed in the browser tab!"))

;;; A place to hang onto the server so we can stop/start it.
(defonce server-ref (volatile! nil))
(defonce zserver-ref (volatile! nil))
;;; A Zotero library private key used for connection:
(defonce private-key  (atom "")) ; <================================================
(def user-id (atom 3000340))

(defn main [& _args]
  (js/console.log "starting server")
  (let [server (http/createServer #(request-handler %1 %2))]
    (.listen server 3000
      (fn [err]
        (if err
          (js/console.error "server start failed")
          (js/console.info "http server running"))
        ))
    (vreset! server-ref server)))

(defn start
  "Hook to start. Also used as a hook for hot code reload."
  []
  (js/console.warn "start called")
  (main))

(defn stop
  "Hot code reload hook to shut down resources so hot code reload can work"
  [done]
  (js/console.warn "stop called")
  (when-some [srv @server-ref]
    (.close srv
      (fn [err]
        (js/console.log "stop completed" err)
        (done)))))

(js/console.log "__filename" js/__filename)

;;;===================================================
(def items (atom nil))

;;; const response = await api().library('user', 475425).collections('9KH9TNSJ').items().get();
;;; const items = response.getData();
;;; console.log(items.map(i => i.title));
(defn get-data []
  (log/info "get-data")
  (let [server (if @private-key
                 (zotero. @private-key)
                 (throw (ex-info "You need to specify the library private key." {:key @private-key})))]
    (vreset! zserver-ref server)
    (reset! items nil)
    (go 
      (try (as-> server ?x
             (.library ?x "user" @user-id)
             ;; You don't have to focus on a particular collection; the .collections step can be commented out.
             ;; Currently, the only way I know of learning collection keys is to run without this and look at :collections key.
             (.collections ?x "9N7626KR")
             (.items ?x)
             (<p! (.get ?x)) ; https://clojurescript.org/guides/promise-interop http GET; .get returns js promise.
             (.getData ?x)
             (js->clj ?x :keywordize-keys true)
             (reset! items ?x))
           (catch js/Error err (js/console.log (ex-cause err)))))))

(defn show-data []
  (log/info "show-data")
  (doall (map #(let [title (:title %)]
                 (when (not= title "PDF")
                   (log/info (str "Title:" title))))
              @items)))

;;; Datascript doesn't like the Datahike-like vector of maps with :db/ident.
;;; :db/valueType is optional in Datascript. In fact, it doesn't like them!
;(def schema [{:db/ident :person/aka :db/cardinality :db.cardinality/many}])
(def schema {:person/name {:db/cardinality :db.cardinality/one  #_#_:db/valueType :db.type/string}
             :person/age  {:db/cardinality :db.cardinality/one  #_#_:db/valueType :db.type/long}
             :person/aka  {:db/cardinality :db.cardinality/many #_#_:db/valueType :db.type/string}})

(def conn (d/create-conn (util/learn-schema! @items)))

(defn show-schema [])

(defn ds-make-db []
    (d/transact! conn [ {:person/name  "Maksim"
                         :person/age   45
                         :person/aka   ["Maks Otto von Stirlitz", "Jack Ryan"] } ]))

(defn ds-queries []
  (log/info "Name:" (d/q '[:find ?n :where [?e :person/name ?n]] @conn))
  (log/info "AKA:"  (d/q '[:find ?n :where [?e :person/aka  ?n]] @conn)))


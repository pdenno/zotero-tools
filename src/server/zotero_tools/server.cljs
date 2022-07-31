(ns zotero-tools.server
  (:require
   [taoensso.timbre :as log]
   [datascript.core :as d]
   ["http" :as http]
   ["zotero-api-client$default" :as zotero])) ; https://shadow-cljs.github.io/docs/UsersGuide.html#js-deps

(defn request-handler [_req res]
  (.end res "This is printed in the browser tab!"))

;;; A place to hang onto the server so we can stop/start it.
(defonce server-ref (volatile! nil))
(defonce zserver-ref (volatile! nil))
;;; A Zotero library private key used for connection:
(defonce private-key  (atom "P6mNyFcD6jebZmpz3tbxmKBx")) ; <========================
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
    (vreset! server-ref server)
    #_(if-let [pkey @private-key]
      (vreset! zserver-ref (zotero. pkey))
      (js/console.error "Supply a Zotero private key."))))

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
(add-tap #(println %))

(def p "When all goes well, the value is a js/promise." (atom nil))
(def response "Response from from Zotero get" (atom nil))

;;;   const response = await api().library('user', 475425).collections('9KH9TNSJ').items().get();
(defn get-data []
  (log/info "get-data")
  (let [server (if @private-key
                 (zotero. @private-key)
                 (throw (ex-info "You need to specify the library private key." {:key @private-key})))]
    (vreset! zserver-ref server)
    (try (-> server
             (.library "user" @user-id)
             ;; You don't have to focus on a particular collection; the .collections step can be commented out.
             ;; Currenlty, the only way I know of learning collection keys is to run without this and look at :collections key.
             (.collections "9N7626KR")
             .items
             .get ; https://clojurescript.org/guides/promise-interop (this is "promise resolve"?)
             (.then #(reset! response  %))
             (.catch #(js/console.log %))
             (.finally #(js/console.log "cleanup")))
       (catch :default e
         (ex-info "get-data didn't go well." {:e e})))))

;;; const items = response.getData();
;;; console.log(items.map(i => i.title));
(defn show-data []
  (log/info "show-data")
  (try 
    (as-> @response ?d
      (.getData ?d)
      (js->clj ?d :keywordize-keys true)
      (doall (map #(let [title (:title %)]
                     (when (not= title "PDF")
                       (log/info "Title:" title)))
                  ?d)))
    (catch :default e
      (ex-info "show-data didn't go well." {:e e}))))

;;; It doesn't like the datahike vector of maps with :db/ident.
;(def schema [{:db/ident :person/aka :db/cardinality :db.cardinality/many}])
(def schema {:person/name {:db/cardinality :db.cardinality/one  :db/valueType :db.type/string}
             :person/age  {:db/cardinality :db.cardinality/one  :db/valueType :db.type/long}
             :person/aka  {:db/cardinality :db.cardinality/many :db/valueType :db.type/string}})

(def conn (d/create-conn schema))

;; Implicit join, multi-valued attribute
(defn ds-tryme []
    (d/transact! conn [ {:person/name  "Maksim"
                         :person/age   45
                         :person/aka   ["Maks Otto von Stirlitz", "Jack Ryan"] } ]))

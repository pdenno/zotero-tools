(ns zotero-tools.util
  (:require
   [datascript.core :as d]
   [datascript.pull-api :as dp]
   [taoensso.timbre :as log]))

(def debugging? nil)
(def diag (atom nil))

(defn no-host&time-output-fn
  "I don't want :hostname_ and :timestamp_ in the log output."
  ([data]       (taoensso.timbre/default-output-fn nil  (dissoc data :hostname_ :timestamp_)))
  ([opts data]  (taoensso.timbre/default-output-fn opts (dissoc data :hostname_ :timestamp_))))

  (defn config-log
  "Configure Timbre: set reporting levels and drop reporting host and time."
  [level]
  (log/set-config!
   (-> log/*config*
       (assoc :output-fn #'no-host&time-output-fn)
       (assoc :min-level [[#{"datahike.*"} :error]
                          [#{"*"} level]]))))

(defn valid-for-transact?
  [data]
  (cond (nil? data) (throw (ex-info "nil is not valid for DH transact." {:nil data})),
        (and (map? data) (empty? data)) (throw (ex-info "{} is not valid for DH transact." {:empty data})),
        (map? data) (reduce-kv (fn [m k v] (assoc m k (valid-for-transact? v))) {} data)
        (vector? data) (mapv valid-for-transact? data)
        (keyword? data) data,
        (string? data) data,
        (number? data) data,
        (boolean? data) data,
        :else (throw (ex-info "unknown datatype" {:unknown data}))))

(defn transact?
  "Optionally check that the data is valid before sending it.
   Does not compare against the schema. Currenlty only checks for nils."
  [conn data & {:keys [check-data?] :or {check-data? debugging?}}]
  (try (when check-data? (valid-for-transact? data))
       (d/transact conn data)
       (catch js/Error err (throw (ex-info "Invalid data for d/transact" {:data data :e err})))))
;;; ToDo: This is from owl-db-tools and uses :resource/iri
(defn resolve-obj
  "Resolve :db/id in the argument map."
  [m conn & {:keys [keep-db-ids?]}]
  (letfn [(subobj [x]
            (cond (and (map? x) (contains? x :resource/iri)) (:resource/iri x),          ; It is a whole resource, return ref.
                  (and (map? x) (contains? x :db/id) (== (count x) 1))                 ; It is an object ref...
                  (or (and (map? x)
                           (contains? x :db/id)
                           (d/q `[:find ?id . :where [~(:db/id x) :resource/iri ?id]] conn)) ; ...return keyword if it is a resource...
                      (subobj (dp/pull conn '[*] (:db/id x)))),                             ; ...otherwise it is some other structure.
                  (map? x) (reduce-kv
                            (fn [m k v] (if (and (= k :db/id) (not keep-db-ids?)) m (assoc m k (subobj v))))
                            {} x),
                  (vector? x) (mapv subobj x),
                  :else x))]
    (-> (reduce-kv (fn [m k v] (assoc m k (subobj v))) {} m)
        (dissoc :db/id))))


;;; -------------- Learning schema -----------------------------
(def multi-valued-property?
  "Properties where the object can have many values
   Many-valued properties aren't the same as things bearing temp values. For example, :owl/complement of can have a temp."
  (atom nil))

(def single-valued-property?
  "The following are properties that can only take one value."
  (atom nil))

(def full-schema "The currently schema, consisting of static-schema plus what is learned while loading." (atom nil))
(def not-stored-property? "These aren't stored; they are used to create vectors." #{})

(defn learn-type
  "Return the :db/valueType for the data."
  [prop examples]
  (let [prop-examples (filter #(= prop (second %)) examples)
        data          (map #(nth % 2) prop-examples)
        types         (->> data (map type) distinct)
        d1            (first data)]
    (if (== 1 (count types))
      (cond (keyword? d1) :db.type/keyword,
            (string? d1)  :db.type/string,
            (int? d1)     :db.type/number,
            (boolean? d1) :db.type/boolean,
            (map? d1)     :db.type/ref,
            :else (throw (ex-info "Cannot learn type for" {:data data})))
      (log/error "Found multiple types while learning" prop types))))

(defn learn-cardinality
  "Return either :db.cardinality/many or :db.cardinality/one based on evidence."
  [prop examples]
  (reset! diag {:prop prop :examples examples})
  (let [prop-examples (filter #(= prop (second %)) examples)
        individuals   (->> prop-examples (map first))
        result (if (== (-> individuals distinct count)
                       (-> individuals count))
                 :db.cardinality/one
                 :db.cardinality/many)]
    (case result
      :db.cardinality/one  (swap! single-valued-property? conj prop)
      :db.cardinality/many (swap! multi-valued-property?  conj prop))
    result))

(defn learn-schema!
  "Create a schema from what we know about the owl, rdfs, and rdf parts
   plus any additional triples created by ontologies."
  [dvecs & {:keys [datascript?] :or {datascript? true}}]
  (try 
    (let [known-property?    (into @single-valued-property? @multi-valued-property?)
          examples           (remove #(known-property? (nth % 1)) dvecs)
          unknown-properties (->> examples (mapv second) distinct)
          learned            (atom [])]
      (doseq [prop unknown-properties]
        (when-not (not-stored-property? prop)
          (when (not-any? #(= prop (:db/ident %)) @full-schema)
            (let [spec #:db{:ident prop
                            :cardinality (learn-cardinality prop examples)
                            :valueType   (learn-type prop examples)
                            :app/origin :learned}]
              (log/debug "learned:" spec)
              (swap! learned #(conj % spec))
              (swap! full-schema #(conj % spec))))))
      (if datascript?
        (reduce (fn [res x] (assoc res (:db/ident x) (dissoc x :db/ident))) [] @learned)
        @learned))
    (catch js/Error err
      (reset! diag err)
      (log/error (ex-info "some error " {:error err}))
      (js/console.log (ex-cause err)))))

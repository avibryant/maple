(ns com.twitter.maple.tap.memory-test
  (:use clojure.test)
  (:require [clojure.string :as s])
  (:import [java.util ArrayList]
           [com.twitter.maple.tap MemorySourceTap]
           [cascading.tuple Fields Tuple]
           [cascading.flow.hadoop HadoopFlowProcess]
           [org.apache.hadoop.mapred JobConf]))

(def ^:dynamic *default-conf* {})

(def defaults
  {"io.serializations"
   (s/join "," ["org.apache.hadoop.io.serializer.WritableSerialization"
                "cascading.tuple.hadoop.TupleSerialization"])})

(def mk-props
  (partial merge defaults))

(defn job-conf
  "Returns a JobConf instance, optionally augmented by the supplied
   property map."
  ([] (job-conf *default-conf*))
  ([prop-map]
     (let [conf (JobConf.)]
       (doseq [[k v] (mk-props prop-map)]
         (.set conf k v))
       conf)))

(defn tuple-seq
  "Returns all tuples in the supplied cascading tap as a Clojure
  sequence."
  [tap]
  (with-open [it (-> (HadoopFlowProcess. (job-conf))
                     (.openTapForRead tap))]
    (doall (for [wrapper (iterator-seq it)]
             (into [] (.getTuple wrapper))))))

(defn collectify [obj]
  (if (or (sequential? obj)
          (instance? java.util.List obj))
    obj, [obj]))

(defn fields
  {:tag Fields}
  [obj]
  (if (or (nil? obj) (instance? Fields obj))
    obj
    (let [obj (collectify obj)]
      (if (empty? obj)
        Fields/ALL ; TODO: add Fields/NONE support
        (Fields. (into-array String obj))))))

(defn coerce-to-tuple [o]
  (Tuple. (if (instance? java.util.List o)
            (.toArray o)
            0)))

(defn memory-tap
  ([tuples] (memory-tap Fields/ALL tuples))
  ([fields-in tuple-seq]
     (let [tuples (ArrayList. (map coerce-to-tuple tuple-seq))]
       (MemorySourceTap. tuples (fields fields-in)))))

(deftest round-trip-tuple-test
  (are [coll] (= coll (tuple-seq (memory-tap coll)))
       [[1] [2]]
       [[1 2] [3 4]]))

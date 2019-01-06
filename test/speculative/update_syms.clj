(ns speculative.update-syms
  (:require
   [clojure.set :as set]
   [clojure.spec.test.alpha :as stest]
   [clojure.string :as str]
   [clojure.data :as data]
   [speculative.core]
   [speculative.impl.syms :as prev-syms]
   [speculative.set]
   [speculative.string]
   [clojure.pprint :refer [pprint]]))

(defn cljsify [syms]
  (map
   (fn [sym]
     (let [ns (namespace sym)
           ns (str/replace ns #"^clojure\.core" "cljs.core")
           sym (symbol ns (name sym))]
       sym))
   syms))

(defn ->sorted-set [coll]
  (apply sorted-set coll))

(defn diff [s1 s2]
  (let [[s1 s2]  (data/diff s1 s2)]
    (if (and (empty? s1) (empty? s2))
      nil
      [s1 s2])))

(defn print-diff [sym curr prev]
  (when-let [[s1 s2] (diff curr prev)]
    (println (str/join " " [sym
                            (when (not-empty s1)
                              (str "added " s1))
                            (when (not-empty s2)
                              (str "removed" s2))]))))

;; Symbols on blacklist have no point of being instrumented, since there is
;; almost no way to call them with wrong arguments, or they are not
;; instrumentable for the enviroment.

(def all-syms (stest/instrumentable-syms))
(def all-syms-clj (->sorted-set all-syms))
(def all-syms-cljs (->sorted-set (cljsify (disj all-syms `re-matcher `re-groups))))
(def blacklist (->sorted-set `[list not some? str = get]))
(def blacklist-clj (->sorted-set blacklist))
(def blacklist-cljs (->sorted-set (cljsify (into blacklist `[next str apply =]))))
(def instrumentable-syms-clj (->sorted-set (set/difference all-syms-clj blacklist-clj)))
(def instrumentable-syms-cljs (->sorted-set (set/difference all-syms-cljs blacklist-cljs)))

(defn -main [& args]
  (println "==== Changes")
  (print-diff "all-syms-clj" all-syms-clj prev-syms/all-syms-clj)
  (print-diff "all-syms-cljs" all-syms-cljs prev-syms/all-syms-cljs)
  (print-diff "blacklist-clj" blacklist-clj prev-syms/blacklist-clj)
  (print-diff "blacklist-cljs" blacklist-cljs prev-syms/blacklist-cljs)
  (print-diff "instrumentable-syms-clj" instrumentable-syms-clj prev-syms/instrumentable-syms-clj)
  (print-diff "instrumentable-syms-cljs" instrumentable-syms-cljs prev-syms/instrumentable-syms-cljs)
  (println "=====")
  (println "Update src/speculative/impl/syms.cljc with changes? Y/n")
  (let [s (read-line)]
    (if (= "Y" s)
      (let [new-file
            (with-out-str
              (binding [clojure.pprint/*print-right-margin* 80]
                (pprint '(ns speculative.impl.syms
                           (:require [clojure.set]
                                     [clojure.string]))))
              (binding [clojure.pprint/*print-right-margin* 7]
                (pprint (list 'def 'all-syms-clj
                              (list 'quote all-syms-clj)))
                (pprint (list 'def 'all-syms-cljs
                              (list 'quote all-syms-cljs)))
                (pprint (list 'def 'blacklist-clj
                              (list 'quote blacklist-clj)))
                (pprint (list 'def 'blacklist-cljs
                              (list 'quote blacklist-cljs)))
                (pprint (list 'def 'instrumentable-syms-clj
                              (list 'quote instrumentable-syms-clj)))
                (pprint (list 'def 'instrumentable-syms-cljs
                              (list 'quote instrumentable-syms-cljs)))))]
        (do (spit "src/speculative/impl/syms.cljc" new-file)
            (println "Succesfully wrote file.")))
      (println "Not updated file."))))
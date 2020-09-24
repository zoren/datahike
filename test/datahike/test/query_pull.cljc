(ns datahike.test.query-pull
  (:require
   #?(:cljs [cljs.test    :as t :refer-macros [is are deftest testing]]
      :clj  [clojure.test :as t :refer        [is are deftest testing]])
   [datahike.test.core :as tdc]
   [datahike.core :as d]))

(def test-db (d/db-with (d/empty-db)
                        [{:db/id tdc/e1 :name "Petr" :age 44}
                         {:db/id tdc/e2 :name "Ivan" :age 25}
                         {:db/id tdc/e3 :name "Oleg" :age 11}]))

(deftest test-basics
  (are [find res] (= (set (d/q {:find find
                                :where '[[?e :age ?a]
                                         [(>= ?a 18)]]}
                               test-db))
                     res)
    '[(pull ?e [:name])]
    #{[{:name "Ivan"}] [{:name "Petr"}]}

    '[(pull ?e [*])]
    #{[{:db/id tdc/e2 :age 25 :name "Ivan"}]
      [{:db/id tdc/e1 :age 44 :name "Petr"}]}

    '[?e (pull ?e [:name])]
    #{[tdc/e2 {:name "Ivan"}]
      [tdc/e1 {:name "Petr"}]}

    '[?e ?a (pull ?e [:name])]
    #{[tdc/e2 25 {:name "Ivan"}]
      [tdc/e1 44 {:name "Petr"}]}

    '[?e (pull ?e [:name]) ?a]
    #{[tdc/e2 {:name "Ivan"} 25]
      [tdc/e1 {:name "Petr"} 44]}))

(deftest test-var-pattern
  (are [find pattern res] (= (set (d/q {:find find
                                        :in   '[$ ?pattern]
                                        :where '[[?e :age ?a]
                                                 [(>= ?a 18)]]}
                                       test-db pattern))
                             res)
    '[(pull ?e ?pattern)] [:name]
    #{[{:name "Ivan"}] [{:name "Petr"}]}

    '[?e ?a ?pattern (pull ?e ?pattern)] [:name]
    #{[tdc/e2 25 [:name] {:name "Ivan"}] [tdc/e1 44 [:name] {:name "Petr"}]}))

;; not supported
#_(deftest test-multi-pattern
    (is (= (set (d/q '[:find ?e ?p (pull ?e ?p)
                       :in $ [?p ...]
                       :where [?e :age ?a]
                       [>= ?a 18]]
                     test-db [[:name] [:age]]))
           #{[2 [:name] {:name "Ivan"}]
             [2 [:age]  {:age 25}]
             [1 [:name] {:name "Petr"}]
             [1 [:age]  {:age 44}]})))

(deftest test-multiple-sources
  (let [db1 (d/db-with (d/empty-db) [{:db/id tdc/e1 :name "Ivan" :age 25}])
        db2 (d/db-with (d/empty-db) [{:db/id tdc/e1 :name "Petr" :age 25}])]
    (is (= (set (d/q '[:find ?e (pull $1 ?e [:name])
                       :in $1 $2
                       :where [$1 ?e :age 25]]
                     db1 db2))
           #{[tdc/e1 {:name "Ivan"}]}))

    (is (= (set (d/q '[:find ?e (pull $2 ?e [:name])
                       :in $1 $2
                       :where [$2 ?e :age 25]]
                     db1 db2))
           #{[tdc/e1 {:name "Petr"}]}))

    (testing "$ is default source"
      (is (= (set (d/q '[:find ?e (pull ?e [:name])
                         :in $1 $
                         :where [$ ?e :age 25]]
                       db1 db2))
             #{[tdc/e1 {:name "Petr"}]})))))

(deftest test-find-spec
  (is (= (d/q '[:find (pull ?e [:name]) .
                :where [?e :age 25]]
              test-db)
         {:name "Ivan"}))

  (is (= (set (d/q '[:find [(pull ?e [:name]) ...]
                     :where [?e :age ?a]]
                   test-db))
         #{{:name "Ivan"} {:name "Petr"} {:name "Oleg"}}))

  (is (= (d/q '[:find [?e (pull ?e [:name])]
                :where [?e :age 25]]
              test-db)
         [tdc/e2 {:name "Ivan"}])))

;; TODO: What does this test test? Is ground needed here?
#_(deftest test-find-spec-input
    (is (= (d/q '[:find (pull ?e ?p) .
                  :in $ ?p
                  :where [(ground tdc/e2) ?e]]
                test-db [:name])
           {:name "Ivan"}))
    (is (= (d/q '[:find (pull ?e p) .
                  :in $ p
                  :where [(ground tdc/e1) ?e]]
                test-db [:name])
           {:name "Ivan"})))

(deftest test-aggregates
  (let [db (d/db-with (d/empty-db {:value {:db/cardinality :db.cardinality/many}})
                      [{:db/id tdc/e1 :name "Petr" :value [10 20 30 40]}
                       {:db/id tdc/e2 :name "Ivan" :value [14 16]}
                       {:db/id tdc/e3 :name "Oleg" :value 1}])]
    (is (= (set (d/q '[:find ?e (pull ?e [:name]) (min ?v) (max ?v)
                       :where [?e :value ?v]]
                     db))
           #{[tdc/e1 {:name "Petr"} 10 40]
             [tdc/e2 {:name "Ivan"} 14 16]
             [tdc/e3 {:name "Oleg"} 1 1]}))))

(deftest test-lookup-refs
  (let [db (d/db-with (d/empty-db {:name {:db/unique :db.unique/identity}})
                      [{:db/id tdc/e1 :name "Petr" :age 44}
                       {:db/id tdc/e2 :name "Ivan" :age 25}
                       {:db/id tdc/e3 :name "Oleg" :age 11}])]
    (is (= (set (d/q '[:find ?ref ?a (pull ?ref [:db/id :name])
                       :in   $ [?ref ...]
                       :where [?ref :age ?a]
                       [(>= ?a 18)]]
                     db [[:name "Ivan"] [:name "Oleg"] [:name "Petr"]]))
           #{[[:name "Petr"] 44 {:db/id tdc/e1 :name "Petr"}]
             [[:name "Ivan"] 25 {:db/id tdc/e2 :name "Ivan"}]}))))

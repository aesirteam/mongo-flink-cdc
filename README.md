## mongo-flink-cdc
### 1. 在Starrocks中创建表
```
CREATE DATABASE mongo;
USE mongo;
CREATE TABLE hrm_org_history (
    oid varchar(24) NOT NULL,
    _class string,
    corpId string,
    _delete string,
    effectDate datetime,
    effectVersion tinyint,
    empId string,
    gmtCreate datetime,
    gmtModified datetime,
    invalidDate datetime,
    operate string,
    operateType string,
    syncDingTalk boolean,
    refKey array<string>
)
PRIMARY KEY (oid)
DISTRIBUTED BY HASH (oid);
```
```
CREATE TABLE hrm_org_history_detail (
    id varchar(32) NOT NULL,
    oid varchar(24) NOT NULL,
    name string,
    value json
)
DUPLICATE KEY (id, oid)
DISTRIBUTED BY HASH (id);
```
### 2. 本地安装启动MongoDB集群及初始化样本数据
```shell
cd docker
docker-compose up -d
```
### 3. 在MongoDB集群导入样本数据
```shell
docker-compose exec mongo11 mongosh mongodb://localhost:27117/test
```
输入：
```
db.collection.insertMany([{
  _class: 'com.renlijia.venus.dal.domain.emp1231231',
  '_id_$oid': '639af1e35fc998688b633873',
  corpId: 'corp_id123',
  delete: 'normal',
  'effectDate_$date': 1664553600000,
  effectVersion: 1,
  empId: 'emp_id123',
  'gmtCreate_$date': 1671098849341,
  'gmtModified_$date': 1671098849341,
  'invalidDate_$date': 4102329600000,
  operate: 'onboarding',
  operateType: 'firstOnBoarding',
  syncDingTalk: 'false',
  workInfo: {
    department: [ 'org_8d8aa6bc0ccf4ee7a485ec65f3d7ebf1' ],
    mainDepartment: 'org_8d8aa6bc0ccf4ee7a485ec65f3d7ebf1'
  },
  workStatusInfo: {
    employeeStatus: 'formal',
    employeeType: 'regular',
    onBoardingDate: { '$date': 1664553600000 },
    probationDate: { '$date': 1664553600000 }
  }
},
{
  _class: 'com.renlijia.venus.dal.domain.emp1231231',
  '_id_$oid': '639af1e35fc998688b633874',
  corpId: 'corp_id234',
  delete: 'normal',
  'effectDate_$date': 1664553600000,
  effectVersion: 1,
  empId: 'emp_id456',
  'gmtCreate_$date': 1671098849341,
  'gmtModified_$date': 1671098849341,
  'invalidDate_$date': 4102329600000,
  operate: 'onboarding',
  operateType: 'firstOnBoarding',
  syncDingTalk: 'false',
  workInfo: {
    department: [ 'org_8d8aa6bc0ccf4ee7a485ec65f3d7ebf1' ],
    mainDepartment: 'org_8d8aa6bc0ccf4ee7a485ec65f3d7ebf1'
  },
  workStatusInfo2: {
    employeeStatus: 'formal',
    employeeType: 'regular',
    onBoardingDate: { '$date': 1664553600000 },
    probationDate: { '$date': 1664553600000 }
  },
  wrokinfo_2: { employ_status: 3 }
},
{
  _class: 'com.renlijia.venus.dal.domain.emp1231231',
  '_id_$oid': '639af1e35fc998688b633875',
  corpId: 'corp_id235',
  delete: 'normal',
  'effectDate_$date': 1664553600000,
  effectVersion: 1,
  empId: 'emp_id345',
  'gmtCreate_$date': 1671098849341,
  'gmtModified_$date': 1671098849341,
  'invalidDate_$date': 4102329600000,
  operate: 'onboarding',
  operateType: 'firstOnBoarding',
  syncDingTalk: 'false',
  workInfo: {
    department: [ 'org_8d8aa6bc0ccf4ee7a485ec65f3d7ebf1' ],
    mainDepartment: 'org_8d8aa6bc0ccf4ee7a485ec65f3d7ebf1'
  },
  workStatusInfo2: {
    employeeStatus: 'formal',
    employeeType: 'regular',
    onBoardingDate: { '$date': 1664553600000 },
    probationDate: { '$date': 1664553600000 }
  },
  workTsk2: { department: 'ni2' },
  nnaskkk: { sadas: 'ni1232' }
}]);
```
### 4. 执行flink算子
```
./bin/start-cluster.sh
./bin/flink run -d -p1 mongo-flink-cdc_2.12-0.1.0-SNAPSHOT.jar \
  --mongo.hosts 'localhost:27117' \
  --mongo.database 'test' \
  --mongo.collection 'collection' \
  --starrocks.fe_nodes 'fe-0:8030,fe-1:8030,fe-2:8030' \
  --starrocks.username 'SR用户' \
  --starrocks.password 'SR密码' \
  --starrocks.database 'mongo'
```
### 5. 连接Starrocks进行表的打宽动作
```
select
  t1.oid,
  t1._class,
  t1.corpId,
  t1._delete, 
  t1.effectDate,
  t1.effectVersion,
  t1.empId,
  t1.gmtCreate,
  t1.gmtModified,
  t1.invalidDate,
  t1.operate,
  t1.operateType,
  t1.syncDingTalk,
  t2.name, 
  get_json_string(t2.value, 'employeeStatus') as employeeStatus, 
  get_json_string(t2.value , 'employeeType') as employeeType,
  from_unixtime(cast (t2.value -> 'onBoardingDate' -> '$date' as bigint)/1000) as onBoardingDate, 
  from_unixtime(cast(t2.value -> 'probationDate' -> '$date' as bigint)/1000) as probationDate,
  get_json_object(t2.value, 'department') as department,
  get_json_string(t2.value, 'mainDepartment') as mainDepartment
from (select *, unnest as _key from hrm_org_history, unnest(refKey) as unnest) t1
left join hrm_org_history_detail t2 on t2.id = t1._key;
```
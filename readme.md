# Kotlin安卓开发-探究ContentProvider

## 一、ContentProvider简介

`ContentProvider`主要用于在不同的应用程序之间实现数据共享的功能，它提供了一套完整的机制，允许一个程序访问另一个程序中的数据，同时还能保证被访问数据的安全性。目前，使用`ContentProvider`是`Android`实现跨程序共享数据的标准方式。

不同于文件存储和`SharedPreferences`存储中的两种全局可读写操作模式，`ContentProvider`可以选择只对哪一部分数据进行共享，从而保证我们程序中的隐私数据不会有泄漏的风险。

在正式开始学习`ContentProvider`之前，我们需要先掌握另外一个非常重要的知识——**Android运行时权限**，以后我们的开发过程中会经常使用运行时权限，因此必须能够牢牢掌握它才行。

---

## 二、运行时权限

Android开发团队在Android 6.0系统中引入了运行时权限这个功能，从而更好地保护了用户的安全和隐私。

### 2.1 Android权限机制详解

当我们需要监听开机广播时，我们需要在AndroidManifest.xml文件中添加这样一句权限声明：

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="com.example.broadcasttest">
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    ...
</manifest>
```

加上这句权限声明之后，如果用户在低于Android 6.0系统的设备上安装该程序，会在安装界面给出提醒。这样用户就可以清楚地知晓该程序一共申请了哪些权限，从而决定是否要安装这个程序。另外，用户可以随时在应用程序管理界面查看任意一个程序的权限申请情况。这样该程序申请的所有权限就尽收眼底，什么都瞒不过用户的眼睛，以此保证应用程序不会出现各种滥用权限的情况。

这种权限机制的设计思路其实非常简单，就是用户如果认可你所申请的权限，就会安装你的程序，如果不认可你所申请的权限，那么拒绝安装就可以了。

Android开发团队在Android 6.0系统中加入了运行时权限功能。也就是说，用户不需要在安装软件的时候一次性授权所有申请的权限，而是可以在软件的使用过程中再对某一项权限申请进行授权。比如一款相机应用在运行时申请了地理位置定位权限，就算我拒绝了这个权限，也应该可以使用这个应用的其他功能，而不是像之前那样直接无法安装它。

当然，并不是所有权限都需要在运行时申请，对于用户来说，不停地授权也很烦琐。Android现在将常用的权限大致归成了两类，一类是普通权限，一类是危险权限。**普通权限**指的是那些**不会直接威胁到用户的安全和隐私的权限**，对于这部分权限申请，系统会自动帮我们进行授权，不需要用户手动操作。**危险权限**则表示那些**可能会触及用户隐私或者对设备安全性造成影响的权限**，如**获取设备联系人信息、定位设备的地理位置**等，对于这部分权限申请，必须由用户手动授权才可以，否则程序就无法使用相应的功能。

但是Android中一共有上百种权限，我们怎么从中区分哪些是普通权限，哪些是危险权限呢？其实并没有那么难，因为危险权限总共就那么些，除了危险权限之外，剩下的大多就是普通权限了。

下表列出了 Android 10 系统为止的危险权限。

|       权限组名       |                            权限名                            |
| :------------------: | :----------------------------------------------------------: |
|       CALENDAR       |              READ_CALENDAR<br />WRITE_CALENDAR               |
|       CALL_LOG       | READ_CALL_LOG<br />WRITE_CALL_LOG<br />PROCESS_OUTGOING_CALLS |
|        CAMERA        |                            CAMERA                            |
|       CONTACTS       |     READ_CONTACTS<br />WRITE_CONTACTS<br />GET_ACCOUNTS      |
|       LOCATION       | ACCESS_FINE_LOCATION<br />ACCESS_COARSE_LOCATION<br />ACCESS_BACKGROUND_LOCATION |
|      MICROPHONE      |                         RECORD_AUDIO                         |
|        PHONE         | READ_PHONE_STATE<br />READ_PHONE_NUMBE RS<br />CALL_PHONE<br />ANSWER_PHONE_CALLS<br />ADD_VOICEMAIL<br />USE_SIP<br />ACCEPT_HANDOVER |
|       SENSORS        |                         BODY_SENSORS                         |
| ACTIVITY_RECOGNITION |                     ACTIVITY_RECOGNITION                     |
|         SMS          | SEND_SMS<br />RECEIVE_SMS<br />READ_SMS<br />RECEIVE_WAP_PUSH<br />RECEIVE_MMS |
|       STORAGE        | READ_EXTERNAL_STORAGE<br />WRITE_EXTERNAL_STORAG<br />EACCESS_MEDIA_LOCATION |

每当要使用一个权限时，可以先到这张表中查一下，如果是这张表中的权限，就需要进行运行时权限处理，否则，只需要在`AndroidManifest.xml`文件中添加一下权限声明就可以了。

另外注意，表格中每个危险权限都属于一个权限组，我们在进行运行时权限处理时使用的是权限名。原则上，用户一旦同意了某个权限申请之后，同组的其他权限也会被系统自动授权。但是请谨记，不要基于此规则来实现任何功能逻辑，因为Android系统随时有可能调整权限的分组。

### 2.2 在程序运行时申请权限

示例使用CALL_PHONE权限编写拨打电话功能的程序，`Intent.ACTION_CALL`则表示直接拨打电话，因此必须声明权限。

修改`AndroidManifest.xml` 声明如下权限：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-feature
        android:name="android.hardware.telephony"
        android:required="false" />
    <uses-permission android:name="android.permission.CALL_PHONE"/>
    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.RuntimePermissionTest"
        tools:targetApi="31">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

接下来实现逻辑部分：

```kotlin
package com.ubx.runtimepermissiontest

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ubx.runtimepermissiontest.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var mainBinding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mainBinding.root)
        mainBinding.makeCall.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CALL_PHONE), 1)
            } else {
                call()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode){
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    call()
                } else {
                    Toast.makeText(this,"u denied the permission", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun call() {
        try {
            val intent = Intent(Intent.ACTION_CALL)
            intent.data = Uri.parse("tel:10086")
            startActivity(intent)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
```

第一步就是要先判断用户是不是已经给过我们授权了，借助的是`ContextCompat.checkSelfPermission()`方法。`checkSelfPermission(`)方法接收两个参数：第一个参数是Context，这个没什么好说的；第二个参数是具体的权限名，比如打电话的权限名就是`android.Manifest.permission.CALL_PHONE`。然后我们使用方法的返回值和`PackageManager.PERMISSION_GRANTED`做比较，相等就说明用户已经授权，不等就表示用户没有授权。

如果已经授权的话就简单了，直接执行拨打电话的逻辑操作就可以了，这里我们把拨打电话的逻辑封装到了`call()`方法当中。如果没有授权的话，则需要调用`ActivityCompat.requestPermissions()`方法向用户申请授权。`requestPermissions()`方法接收3个参数：第一个参数要求是Activity的实例；第二个参数是一个String数组，我们把要申请的权限名放在数组中即可；第三个参数是请求码，只要是唯一值就可以了，这里传入了1。

调用完`requestPermissions()`方法之后，系统会弹出一个权限申请的对话框，用户可以选择同意或拒绝我们的权限申请。不论是哪种结果，最终都会回调到`onRequestPermissionsResult()`方法中，而授权的结果则会封装在`grantResults`参数当中。这里我们只需要判断一下最后的授权结果：如果用户同意的话，就调用`call()`方法拨打电话；如果用户拒绝的话，我们只能放弃操作，并且弹出一条失败提示。

---

## 三、访问其他程序中的数据

`ContentProvider`的用法一般有两种：一种是使用现有的`ContentProvider`读取和操作相应程序中的数据；另一种是创建自己的`ContentProvider`，给程序的数据提供外部访问接口。

如果一个应用程序通过`ContentProvider`对其数据提供了外部访问接口，那么任何其他的应用程序都可以对这部分数据进行访问。Android系统中自带的通讯录、短信、媒体库等程序都提供了类似的访问接口，这就使得第三方应用程序可以充分地利用这部分数据实现更好的功能。

### 3.1 ContentProvider的基本用法

对于每一个应用程序来说，如果想要访问`ContentProvider`中共享的数据，就一定要借助`ContentResolver`类，可以通过Context中的`getContentResolver()`方法获取该类的实例。`ContentResolver`中提供了一系列的方法用于对数据进行增删改查操作，其中`insert()`方法用于添加数据，`update()`方法用于更新数据，`delete()`方法用于删除数据，`query()`方法用于查询数据。有没有似曾相识的感觉？没错，`SQLiteDatabase`中也是使用这几个方法进行增删改查操作的，只不过它们在方法参数上稍微有一些区别。

不同于`SQLiteDatabase`，`ContentResolver`中的增删改查方法都是不接收表名参数的，而是使用一个Uri参数代替，这个参数被称为内容URI。内容URI给`ContentProvider`中的数据建立了唯一标识符，它主要由两部分组成：authority和path。authority是用于对不同的应用程序做区分的，一般为了避免冲突，会采用应用包名的方式进行命名。比如某个应用的包名是`com.example.app`，那么该应用对应的authority就可以命名为`com.example.app.provider`。path则是用于对同一应用程序中不同的表做区分的，通常会添加到authority的后面。比如某个应用的数据库里存在两张表table1和table2，这时就可以将path分别命名为/table1和/table2，然后把authority和path进行组合，内容URI就变成了`com.example.app.provider/table1`和`com.example.app.provider/table2`。不过，目前还很难辨认出这两个字符串就是两个内容URI，我们还需要在字符串的头部加上协议声明。因此，内容URI最标准的格式如下：

```kotlin
content://com.example.app.provider/table1
content://com.example.app.provider/table2
```

内容URI可以非常清楚地表达我们想要访问哪个程序中哪张表里的数据。也正是因此，`ContentResolver`中的增删改查方法才都接收Uri对象作为参数。如果使用表名的话，系统将无法得知我们期望访问的是哪个应用程序里的表。

在得到了内容URI字符串之后，我们还需要将它解析成Uri对象才可以作为参数传入。解析的方法也相当简单，代码如下所示：

```kotlin
val uri = Uri.parse("content://com.example.app.provider/table1")
```

只需要调用`Uri.parse()`方法，就可以将内容URI字符串解析成Uri对象了。

现在就可以使用这个Uri对象查询table1表中的数据了，代码如下所示：

```kotlin
val cursor = contentResolver.query(
    uri,
    projection,
    selection,
    selectionArgs,
    sortOrder
)
```

这些参数和`SQLiteDatabase`中`query()`方法里的参数很像，但总体来说要简单一些，毕竟这是在访问其他程序中的数据，没必要构建过于复杂的查询语句。下表对使用到的这部分参数进行了详细的解释。

| query()方法参数 |        对应SQL部分        |               描述               |
| :-------------: | :-----------------------: | :------------------------------: |
|       uri       |      from table_name      | 指定查询某个应用程序下的某一张表 |
|   projection    |  select column1, column2  |          指定查询的列名          |
|    selection    |   where column = value    |       指定where的约束条件        |
|  selectionArgs  |             -             |  为where中的占位符提供具体的值   |
|    sortOrder    | order by column1, column2 |      指定查询结果的排序方式      |

查询完成后返回的仍然是一个Cursor对象，这时我们就可以将数据从Cursor对象中逐个读取出来了。读取的思路仍然是通过移动游标的位置遍历Cursor的所有行，然后取出每一行中相应列的数据，代码如下所示：

```kotlin
while (cursor.moveToNext()) {
    val column1 = cursor.getString(cursor.getColumnIndex("column1"))
    val column2 = cursor.getString(cursor.getColumnIndex("column2"))
}
cursor.close()
```

table1表中添加一条数据，代码如下所示：

```kotlin
val values = contentValueOf("column1" to "text", "column2" to 1)
contentResolver.insert(uri, values)
```

仍然是将待添加的数据组装到`ContentValues`中，然后调用`ContentResolver`的`insert()`方法，将`Uri`和`ContentValues`作为参数传入即可。

如果我们想要更新这条新添加的数据，把`column1`的值清空，可以借助`ContentResolver`的`update()`方法实现，代码如下所示：

```kotlin
val values = contentValueOf("column1" to "")
contentResolver.update(uri, values, "column1 = ? and column2 = ?", arrayOf("text", "1"))
```

上述代码使用了`selection`和`selectionArgs`参数来对想要更新的数据进行约束，以防止所有的行都会受影响。

最后，可以调用`ContentResolver`的`delete()`方法将这条数据删除掉，代码如下所示：

```kotlin
contentResolver.delete(uri, "column2 = ?", arrayOf("1"))
```

### 3.2 读取系统联系人

权限声明：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.READ_CONTACTS" />
    ...

</manifest>
```

完整代码：

```kotlin
package com.ubx.contectstest

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ubx.contectstest.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var mainBinding: ActivityMainBinding
    private val contactsList = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mainBinding.root)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, contactsList)
        mainBinding.contactsView.adapter = adapter
        // 检查是否获得读取联系人权限
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.READ_CONTACTS), 1)
        } else {
            readContacts()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty()
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    readContacts()
                } else {
                    Toast.makeText(this, "u denied the permission", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @SuppressLint("Range")
    private fun readContacts() {
        // 查询联系人数据
        contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null, null)?.apply {
                while (moveToNext()) {
                    // 获取联系人姓名
                    val displayName = getString(getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                    // 获取联系人手机号
                    val number = getString(getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    // 将获取到的联系人添加到contactList中
                    contactsList.add("$displayName\n$number")
                }
            adapter.notifyDataSetChanged()
            close()
        }
    }
}
```

在`onCreate()`方法中，我们首先按照`ListView`的标准用法对其初始化，然后开始调用运行时权限的处理逻辑，因为`READ_CONTACTS`权限属于危险权限。这里我们在用户授权之后，调用`readContacts()`方法读取系统联系人信息。
下面重点看一下`readContacts()`方法，可以看到，这里使用了`ContentResolver`的`query()`方法查询系统的联系人数据。不过传入的Uri参数怎么有些奇怪啊？为什么没有调用`Uri.parse()`方法去解析一个内容URI字符串呢？这是因为`ContactsContract.CommonDataKinds.Phone`类已经帮我们做好了封装，提供了一个`CONTENT_URI`常量，而这个常量就是使用`Uri.parse()`方法解析出来的结果。接着我们对`query()`方法返回的`Cursor`对象进行遍历，这里使用了?.操作符和apply函数来简化遍历的代码。在apply函数中将联系人姓名和手机号逐个取出，**联系人姓名**这一列对应的常量是**`ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME`**，**联系人手机号**这一列对应的常量是**`ContactsContract.CommonDataKinds.Phone.NUMBER`**。将两个数据取出后进行拼接，并且在中间加上换行符，然后将拼接后的数据添加到`ListView`的数据源里，并通知刷新一下`ListView`，最后千万不要忘记将`Cursor`对象关闭。

---

## 四、创建自己的ContentProvider

### 4.1 创建ContentProvider的步骤

如果想要实现跨程序共享数据的功能，可以通过新建一个类去继承`ContentProvider`的方式来实现。`ContentProvider`类中有6个抽象方法，我们在使用子类继承它的时候，需要将这6个方法全部重写。观察下面的代码示例：

```kotlin
class MyProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        return false
    }
    
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        retutn null
    }
    
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }
    
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        return 0
    }
    
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        return 0
    }
    
    override fun getType(uri: Uri): String? {
        return null
    }
}
```

1. `onCreate()`。初始化`ContentProvider`的时候调用。通常会在这里完成对数据库的创建和升级等操作，返回true表示`ContentProvider`初始化成功，返回false则表示失败。
2. `query()`。从`ContentProvider`中查询数据。`uri`参数用于确定查询哪张表，`projection`参数用于确定查询哪些列，`selection`和`selectionArgs`参数用于约束查询哪些行，`sortOrder`参数用于对结果进行排序，查询的结果存放在`Cursor`对象中返回。
3. `insert()`。向`ContentProvider`中添加一条数据。`uri`参数用于确定要添加到的表，待添加的数据保存在`values`参数中。添加完成后，返回一个用于表示这条新记录的URI。
4. `update()`。更新`ContentProvider`中已有的数据。`uri`参数用于确定更新哪一张表中的数据，新数据保存在`values`参数中，`selection`和`selectionArgs`参数用于约束更新哪些行，受影响的行数将作为返回值返回。
5. `delete()`。从`ContentProvider`中删除数据。`uri`参数用于确定删除哪一张表中的数据，`selection`和`selectionArgs`参数用于约束删除哪些行，被删除的行数将作为返回值返回。
6. `getType()`。根据传入的内容URI返回相应的MIME类型。

可以看到，很多方法里带有`uri`这个参数，这个参数也正是调用`ContentResolver`的增删改查方法时传递过来的。而现在我们需要对传入的`uri`参数进行解析，从中分析出调用方期望访问的表和数据。

回顾一下，一个标准的内容URI写法是：

```kotlin
content://com.example.app.provider/table1
```

这就表示调用方期望访问的是`com.example.app`这个应用的table1表中的数据。

除此之外，我们还可以在这个内容URI的后面加上一个id，例如：

```kotlin
content://com.example.app.provider/table1/1
```

这就表示调用方期望访问的是`com.example.app`这个应用的table1表中id为1的数据。

内容URI的格式主要就只有以上两种，以路径结尾表示期望访问该表中所有的数据，以id结尾表示期望访问该表中拥有相应id的数据。我们可以使用通配符分别匹配这两种格式的内容URI，规则如下。

- *****表示**匹配任意长度的任意*字符***。
- **#**表示**匹配任意长度的任意*数字***。

所以，一个能够匹配任意表的内容URI格式就可以写成：

```kotlin
content://com.example.app.provider/*
```

一个能够匹配table1表中任意一行数据的内容URI格式就可以写成：

```kotlin
content://com.example.app.provider/table1/#
```

接着，我们再借助`UriMatcher`这个类就可以轻松地实现匹配内容URI的功能。`UriMatcher`中提供了一个`addURI()`方法，这个方法接收3个参数，可以分别把`authority`、`path`和一个自定义代码传进去。这样，当调用`UriMatcher`的`match()`方法时，就可以将一个Uri对象传入，返回值是某个能够匹配这个Uri对象所对应的自定义代码，利用这个代码，我们就可以判断出调用方期望访问的是哪张表中的数据了。修改`MyProvider`中的代码，如下所示：

```kotlin
class MyProvider : ContentProvider() {
    private val table1Dir = 0
    private val table1Item = 1
    private val table2Dir = 2
    private val table2Item = 3
    
    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH)
    
    init {
        uriMatcher.addURI("com.example.app.provider ", "table1", table1Dir)
        uriMatcher.addURI("com.example.app.provider ", "table1/#", table1Item)
        uriMatcher.addURI("com.example.app.provider ", "table2", table2Dir)
        uriMatcher.addURI("com.example.app.provider ", "table2/#", table2Item)
    }
    ...
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        when (uriMatcher.match(uri)) {
            table1Dir -> {
                // 查询table1表中的所有数据
            }
            table1Item -> {
                // 查询table1表中的单条数据
            }
            table2Dir -> {
                // 查询table2表中的所有数据
            }
            table2Item -> {
                // 查询table2表中的单条数据
            }
        }
        ...
    }
    ...
}
```

可以看到，`MyProvider`中新增了4个整型变量，其中`table1Dir`表示访问`table1`表中的所有数据，`table1Item`表示访问`table1`表中的单条数据，`table2Dir`表示访问`table2`表中的所有数据，`table2Item`表示访问`table2`表中的单条数据。接着我们在`MyProvider`类实例化的时候立刻创建了`UriMatcher`的实例，并调用`addURI()`方法，将期望匹配的内容URI格式传递进去，注意这里传入的路径参数是可以使用通配符的。然后当`query()`方法被调用的时候，就会通过`UriMatcher`的`match()`方法对传入的Uri对象进行匹配，如果发现`UriMatcher`中某个内容URI格式成功匹配了该Uri对象，则会返回相应的自定义代码，然后我们就可以判断出调用方期望访问的到底是什么数据了。

上述代码只是以`query()`方法为例做了个示范，其实`insert()`、`update()`、`delete()`这几个方法的实现是差不多的，它们都会携带`uri`这个参数，然后同样利用`UriMatcher`的`match()`方法判断出调用方期望访问的是哪张表，再对该表中的数据进行相应的操作就可以了。

除此之外，还有一个方法你可能会比较陌生，即`getType()`方法。它是所有的`ContentProvider`都必须提供的一个方法，用于获取Uri对象所对应的MIME类型。**一个内容URI所对应的MIME字符串主要由3部分组成**，Android对这3个部分做了如下格式规定。

- 必须以`vnd`开头。
- 如果内容URI以路径结尾，则后接`android.cursor.dir/`；如果内容URI以id结尾，则后接`android.cursor.item/`。
- 最后接上`vnd.<authority>.<path>`。

所以，对于`content://com.example.app.provider/table1`这个内容URI，它所对应的MIME类型就可以写成：

```kotlin
vnd.android.cursor.dir/vnd.com.example.app.provider.table1
```

对于`content://com.example.app.provider/table1/1`这个内容URI，它所对应的MIME类型就可以写成：

```kotlin
vnd.android.cursor.item/vnd.com.example.app.provider.table1
```

现在我们可以继续完善`MyProvider`中的内容了，这次来实现`getType()`方法中的逻辑，代码如下所示：

```kotlin
class MyProvider : ContentProvider() {
    ...
    override fun getType(uri: Uri) = when (uriMatcher.match(uri)) {
        table1Dir -> "vnd.android.cursor.dir/vnd.com.example.app.provider.table1"
        table1Item -> "vnd.android.cursor.item/vnd.com.example.app.provider.table1"
        table2Dir -> "vnd.android.cursor.dir/vnd.com.example.app.provider.table2"
        table2Item -> "vnd.android.cursor.item/vnd.com.example.app.provider.table2"
        else -> null
    }
}
```

到这里，一个完整的`ContentProvider`就创建完成了，现在任何一个应用程序都可以使用`ContentResolver`访问我们程序中的数据。那么，如何才能保证隐私数据不会泄漏出去呢？其实多亏了`ContentProvider`的良好机制，这个问题在不知不觉中已经被解决了。因为所有的增删改查操作都一定要匹配到相应的内容URI格式才能进行，而我们当然不可能向`UriMatcher`中添加隐私数据的URI，所以这部分数据根本无法被外部程序访问，安全问题也就不存在了。

### 4.2 实现跨程序数据共享

在之前的`DatabaseTest`项目中创建一个`ContentProvider`，

右击`work.icu007.databasetest`包 → New → Other → Content Provider。

将`ContentProvider`命名为`DatabaseProvider`，将`authority`指定为 `work.icu007.databasetest.provider`，`Exported`属性表示是否允许外部程序访问我们的`ContentProvider`，`Enabled`属性表示是否启用这个`ContentProvider`。将两个属性都勾中，点击“Finish”完成创建。

修改`DatabaseProvider`中的代码，如下所示：

```kotlin
package work.icu007.databasetest

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri

class DataBaseProvider : ContentProvider() {

    private val bookDir = 0
    private val bookItem = 1
    private val categoryDir = 2
    private val categoryItem = 3
    private val authority = "work.icu007.databasetest.provider"
    private var dbHelper: MyDatabaseHelper? = null

    private val uriMatcher by lazy {
        val matcher = UriMatcher(UriMatcher.NO_MATCH)
        matcher.addURI(authority, "book", bookDir)
        matcher.addURI(authority, "book/#", bookItem)
        matcher.addURI(authority, "category", categoryDir)
        matcher.addURI(authority, "category/#", categoryItem)
        matcher
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = dbHelper?.let {
        // 删除数据
        val db = it.writableDatabase
        val deletedRows = when (uriMatcher.match(uri)){
            bookDir -> db.delete("Book", selection, selectionArgs)
            bookItem -> {
                val bookId = uri.pathSegments[1]
                db.delete("Category", "id = ?", arrayOf(bookId))
            }
            categoryDir -> db.delete("Category", selection, selectionArgs)
            categoryItem -> {
                val categoryId = uri.pathSegments[1]
                db.delete("Category", "id = ?", arrayOf(categoryId))
            }
            else -> 0
        }
        deletedRows
    } ?: 0

    override fun getType(uri: Uri) = when (uriMatcher.match(uri)) {
        bookDir -> "vnd.android.cursor.dir/vnd.work.icu007.databasetest.provider.book"
        bookItem -> "vnd.android.cursor.dir/vnd.work.icu007.databasetest.provider.book"
        categoryDir -> "vnd.android.cursor.dir/vnd.work.icu007.databasetest.provider.category"
        categoryItem -> "vnd.android.cursor.dir/vnd.work.icu007.databasetest.provider.category"
        else -> null
    }

    override fun insert(uri: Uri, values: ContentValues?) = dbHelper?.let {
        // 添加数据
        val db = it.writableDatabase
        val uriReturn = when (uriMatcher.match(uri)) {
            bookDir, bookItem -> {
                val newBookId = db.insert("Book", null, values)
                Uri.parse("content://$authority/book/$newBookId")
            }
            categoryDir, categoryItem -> {
                val newCategoryId = db.insert("Category", null, values)
                Uri.parse("content://$authority/category/$newCategoryId")
            }
            else -> null
        }
        uriReturn
    }

    override fun onCreate() = context?.let {
        dbHelper = MyDatabaseHelper(it, "BookStore.db", 2)
        true
    } ?: false

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ) = dbHelper?.let {
        // 查询数据
        val  db = it.writableDatabase
        val cursor = when (uriMatcher.match(uri)) {
            bookDir -> db.query("Book", projection, selection, selectionArgs, null, null, sortOrder)
            bookItem -> {
                val bookId = uri.pathSegments[1]
                db.query("Book", projection, "id = ?", arrayOf(bookId), null, null, sortOrder)
            }
            categoryDir -> db.query("Category", projection, selection, selectionArgs, null, null, sortOrder)
            categoryItem -> {
                val categoryId = uri.pathSegments[1]
                db.query("Category", projection, "id = ?", arrayOf(categoryId), null, null, sortOrder)
            }
            else -> null
        }
        cursor
    }

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<String>?
    ) = dbHelper?.let {
        // 更新数据
        val db = it.writableDatabase
        val updateRows = when (uriMatcher.match(uri)) {
            bookDir -> db.update("Book", values, selection, selectionArgs)
            bookItem -> {
                val bookId = uri.pathSegments[1]
                db.update("Book", values, "id = ?", arrayOf(bookId))
            }
            categoryDir -> db.update("Category", values, selection, selectionArgs)
            categoryItem -> {
                val categoryId = uri.pathSegments[1]
                db.update("Category", values, "id = ?", arrayOf(categoryId))
            }
            else -> 0
        }
        updateRows
    } ?: 0
}
```

在类的一开始，同样是定义了4个变量，分别用于表示**访问Book表中的所有数据**、**访问Book表中的单条数据**、**访问Category表中的所有数据**和访问**Category表中的单条数据**。然后在一个`by lazy`代码块里对`UriMatcher`进行了初始化操作，将期望匹配的几种URI格式添加了进去。

`by lazy`代码块是`Kotlin`提供的一种**懒加载技术**，**代码块中的代码一开始并不会执行**，只有当`uriMatcher`变量**首次被调用的时候才会执行**，并且会将代码块中**最后一行代码的返回值赋给`uriMatcher`**

接下来就是每个抽象方法的具体实现：

- `delete()`方法：是先获取`SQLiteDatabase`的实例，然后根据传入的`uri`参数判断用户想要删除哪张表里的数据，再调用`SQLiteDatabase`的delete()方法进行删除，被删除的行数将作为返回值返回。
- `getType()`方法：完全是按照上一节中介绍的格式规则编写的，1. 以`vnd`开头。如果内容URI以路径结尾，则后接`android.cursor.dir/`；2. 如果内容URI以id结尾，则后接`android.cursor.item/`；3. 最后接上`vnd.<authority>.<path>`。
- `insert()`方法：也是先获取了`SQLiteDatabase`的实例，然后根据传入的Uri参数判断用户想要往哪张表里添加数据，再调用`SQLiteDatabase`的`insert()`方法进行添加就可以了。注意，**insert()方法要求返回一个能够表示这条新增数据的URI**，所以我们还需要调用`Uri.parse()`方法，将一个内容URI解析成Uri对象，当然这个内容URI是以新增数据的id结尾的。
- `onCreate()`方法：这个方法的代码很短，但是语法可能有点特殊。这里**综合利用了Getter方法语法糖、?.操作符、let函数、?:操作符以及单行代码函数语法糖**。首先调用了`getContext()`方法并借助`?.`操作符和`let`函数判断它的返回值是否为空：**如果为空就使用`?:`操作符返回false**，表示`ContentProvider`初始化失败；**如果不为空就执行let函数中的代码**。在`let`函数中创建了一个`MyDatabaseHelper`的实例，然后**返回true表示`ContentProvider`初始化成功。**
- `query()`方法：在这个方法中先获取了`SQLiteDatabase`的实例，然后根据传入的Uri参数判断用户想要访问哪张表，再调用`SQLiteDatabase`的`query()`进行查询，**并将Cursor对象返回**就好了。注意，**当访问单条数据的时候**，调用了Uri对象的`getPathSegments()`方法，**它会将内容URI权限之后的部分以“/”符号进行分割**，并把分割后的结果放入一个字符串列表中，那这个列表的**第0个位置存放的就是路径**，**第1个位置存放的就是id**了。得到了id之后，再通过`selection`和`selectionArgs`参数进行约束，就实现了查询单条数据的功能。
- `update()`方法：相信这个方法中的代码已经完全难不倒你了，也是先获取`SQLiteDatabase`的实例，然后根据传入的`uri`参数判断用户想要更新哪张表里的数据，再调用`SQLiteDatabase`的`update()`方法进行更新就好了，受影响的行数将作为返回值返回。

另外，还有一点需要注意，**`ContentProvider`一定要在AndroidManifest.xml文件中注册才可以使用**。不过幸运的是，我们是使用Android Studio的快捷方式创建的`ContentProvider`，因此注册这一步已经自动完成了。打开`AndroidManifest.xml`文件，代码如下：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.DatabaseTest"
        tools:targetApi="31">
        <provider
            android:name=".DataBaseProvider"
            android:authorities="work.icu007.databasetest.provider"
            android:enabled="true"
            android:exported="true">
        </provider>

        ...
    </application>

</manifest>
```

`<application>`标签内出现了一个新的标签<provider>，我们使用它来对`DatabaseProvider`进行注册。`android:name`属性指定了`DatabaseProvider`的**类名**，`android:authorities`属性指定了`DatabaseProvider`的**authority**，而enabled和exported属性则是根据我们刚才勾选的状态自动生成的，这里表示允许`DatabaseProvider`被其他应用程序访问。

现在`DatabaseTest`这个项目就已经拥有了跨程序共享数据的功能了，将`DatabaseTest`程序重新安装在模拟器上。接着关闭`DatabaseTest`这个项目，并创建一个新项目`ProviderTest`，我们将通过这个程序去访问`DatabaseTest`中的数据。

代码如下：

```kotlin
package com.ubx.providertest

import android.annotation.SuppressLint
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.core.content.contentValuesOf
import com.ubx.providertest.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private lateinit var mainBinding: ActivityMainBinding
    private var bookId: String? = null
    @SuppressLint("Range")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mainBinding.root)

        mainBinding.addData.setOnClickListener {
            // add Data
            val uri = Uri.parse("content://work.icu007.databasetest.provider/book")
            val values = contentValuesOf("name" to "剑来", "author" to "烽火戏诸侯", "pages" to 60000,
                "price" to 50.99)
            val newUri = contentResolver.insert(uri, values)
            bookId = newUri?.pathSegments?.get(1)
            Log.d(TAG, "addData: bookId : $bookId ")
        }

        mainBinding.queryData.setOnClickListener {
            // query Data
            val uri = Uri.parse("content://work.icu007.databasetest.provider/book")
            contentResolver.query(uri, null, null, null, null)?.apply {
                while (moveToNext()){
                    val name = getString(getColumnIndex("name"))
                    val author = getString(getColumnIndex("author"))
                    val pages = getInt(getColumnIndex("pages"))
                    val price = getDouble(getColumnIndex("price"))
                    Log.d(TAG, "onCreate: book name is $name;\nbook author is $author;\n" +
                            "book pages is $pages;\nbook price is $price.")
                }
                close()
            }
        }

        mainBinding.updateData.setOnClickListener {
            // update Data
            bookId?.let {
                val uri = Uri.parse("content://work.icu007.databasetest.provider/book/$it")
                val values = contentValuesOf("name" to "雪中悍刀行", "pages" to 50000, "price" to 49.99)
                contentResolver.update(uri, values, null, null)
            }
        }

        mainBinding.deleteData.setOnClickListener {
            // delete data
            Log.d(TAG, "deleteData: bookId : $bookId")
            bookId?.let {
                val uri = Uri.parse("content://work.icu007.databasetest.provider/book/$it")
                contentResolver.delete(uri, null, null)
                Log.d(TAG, "LCR deleteData: delete $it")
            }
        }
    }
}
```

我们分别在这4个按钮的点击事件里面处理了增删改查的逻辑。添加数据的时候，首先调用了`Uri.parse()`方法将一个内容URI解析成Uri对象，然后把要添加的数据都存放到`ContentValues`对象中，接着调用`ContentResolver`的insert()方法执行添加操作就可以了。注意，`insert()`方法会返回一个Uri对象，这个对象中包含了新增数据的`id`，我们通过`getPathSegments()`方法将这个id取出，稍后会用到它。

查询数据的时候，同样是调用了`Uri.parse()`方法将一个内容URI解析成Uri对象，然后调用`ContentResolver`的query()方法查询数据，查询的结果当然还是存放在`Cursor`对象中。之后对`Cursor`进行遍历，从中取出查询结果，并一一打印出来。

更新数据的时候，也是先将内容URI解析成Uri对象，然后把想要更新的数据存放到`ContentValues`对象中，再调用`ContentResolver`的`update()`方法执行更新操作就可以了。注意，这里我们为了不想让`Book`表中的其他行受到影响，在调用`Uri.parse()`方法时，给内容URI的尾部增加了一个id，而这个id正是添加数据时所返回的。这就表示我们只希望更新刚刚添加的那条数据，`Book`表中的其他行都不会受影响。

删除数据的时候，也是使用同样的方法解析了一个以id结尾的内容URI，然后调用`ContentResolver`的`delete()`方法执行删除操作就可以了。由于我们在内容URI里指定了一个id，因此只会删掉拥有相应id的那行数据，Book表中的其他数据都不会受影响。

---

## 五、小结

本篇文章我们一开始先了解了Android的权限机制，并且学会了如何在`Android 6.0`以上的系统中使用运行时权限，然后重点学习了`ContentProvider`的相关内容，以实现跨程序数据共享的功能。现在我们不仅知道了如何访问其他程序中的数据，还学会
了怎样创建自己的`ContentProvider`来共享数据。

不过，每次在创建`ContentProvider`的时候，你都需要提醒一下自己，我是不是应该这么做？因为只有在真正需要将数据共享出去的时候才应该创建`ContentProvider`，如果仅仅是用于程序内部访问的数据，就没有必要这么做，所以千万别对它进行滥用。


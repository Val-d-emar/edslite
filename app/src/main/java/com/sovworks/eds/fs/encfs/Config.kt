package com.sovworks.eds.fs.encfs

import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.util.Base64
import com.sovworks.eds.android.Logger.Companion.showAndLog
import com.sovworks.eds.android.R
import com.sovworks.eds.exceptions.ApplicationException
import com.sovworks.eds.fs.Directory
import com.sovworks.eds.fs.File
import com.sovworks.eds.fs.Path
import com.sovworks.eds.fs.encfs.codecs.data.AESDataCodecInfo
import com.sovworks.eds.fs.encfs.codecs.name.BlockNameCodecInfo
import com.sovworks.eds.fs.util.PathUtil
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class Config {
    @Throws(IOException::class, ApplicationException::class)
    fun read(pathToRootFolder: Path) {
        val p = getConfigFilePath(pathToRootFolder.directory)
        if (p != null) read(p.file)
        else throw ApplicationException("EncFs config file doesn't exist")
    }

    @Throws(IOException::class, ApplicationException::class)
    fun read(configFile: File) {
        val inp = configFile.inputStream
        try {
            read(inp)
        } finally {
            inp.close()
        }
    }

    @Throws(ApplicationException::class)
    fun read(config: InputStream?) {
        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder: DocumentBuilder
        try {
            dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(config)
            //optional, but recommended
            //read this - http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
            doc.documentElement.normalize()
            val nl = doc.getElementsByTagName("cfg")
            require(nl.length != 0) { "cfg element not found" }
            val n = nl.item(0)
            require(n.nodeType == Node.ELEMENT_NODE) { "wrong document structure" }
            readCfgElement(n as Element)
        } catch (e: Exception) {
            throw ApplicationException("Failed reading the config file", e)
        }
    }

    @Throws(IOException::class, ApplicationException::class)
    fun write(pathToRootFolder: Path?) {
        write(PathUtil.getFile(pathToRootFolder, CONFIG_FILENAME))
    }

    @Throws(IOException::class, ApplicationException::class)
    fun write(configFile: File) {
        val out = configFile.outputStream
        try {
            write(out)
        } finally {
            out.close()
        }
    }

    @Throws(ApplicationException::class)
    fun write(out: OutputStream?) {
        try {
            val docFactory = DocumentBuilderFactory.newInstance()
            val docBuilder = docFactory.newDocumentBuilder()

            val doc = docBuilder.newDocument()
            doc.xmlStandalone = true
            val el = doc.createElement("boost_serialization")
            doc.appendChild(el)
            el.setAttribute("signature", "serialization::archive")
            el.setAttribute("version", "14")
            el.appendChild(makeCfgElement(doc))

            val transformerFactory = TransformerFactory.newInstance()
            val transformer = transformerFactory.newTransformer()
            transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "boost_serialization")
            val source = DOMSource(doc)

            val result = StreamResult(out)
            transformer.transform(source, result)
        } catch (e: Exception) {
            throw ApplicationException("Failed making EncFs config file", e)
        }
    }

    fun initNew(context: Context?) {
        _creator = makeCreatorString(context)
        _subVersion = 20100713
        kDFIterations = 100000
        _desiredKDFDuration = 500
        _keySizeBits = 192
        blockSize = 1024
        mACBytes = 0
        mACRandBytes = mACBytes
        _uniqueIV = true
        _chainedNameIV = true
        _externalIVChaining = false
        _allowHoles = true
        dataCodecInfo = AESDataCodecInfo().select(this) as DataCodecInfo
        nameCodecInfo = BlockNameCodecInfo().select(this) as NameCodecInfo
    }

    fun useUniqueIV(): Boolean {
        return _uniqueIV
    }

    fun useUniqueIV(`val`: Boolean) {
        _uniqueIV = `val`
    }

    var keySize: Int
        get() = _keySizeBits / 8
        set(numBytes) {
            _keySizeBits = numBytes * 8
        }

    fun useChainedNameIV(): Boolean {
        return _chainedNameIV
    }

    fun useChainedNameIV(`val`: Boolean) {
        _chainedNameIV = `val`
    }

    fun useExternalFileIV(): Boolean {
        return _externalIVChaining
    }

    fun useExternalFileIV(`val`: Boolean) {
        _externalIVChaining = `val`
    }

    fun allowHoles(`val`: Boolean) {
        _allowHoles = `val`
    }

    fun allowHoles(): Boolean {
        return _allowHoles
    }

    private var _creator: String? = null
    private var _subVersion = 0
    var dataCodecInfo: DataCodecInfo? = null
    var nameCodecInfo: NameCodecInfo? = null
    private var _keySizeBits = 0
    var blockSize: Int = 0
        set(val) {
            field = `val`
        }

    var encryptedVolumeKey: ByteArray?
        set(val) {
            field = `val`
        }
    var salt: ByteArray?

    var kDFIterations: Int = 0
        set(val) {
            field = `val`
        }
    private var _desiredKDFDuration = 0

    var mACBytes: Int = 0 // MAC headers on blocks..
        set(val) {
            field = `val`
        }
    var mACRandBytes: Int = 0 // number of random bytes in the block header
        set(val) {
            field = `val`
        }

    private var _uniqueIV = false // per-file Initialization Vector
    private var _externalIVChaining = false // IV seeding by filename IV chaining

    private var _chainedNameIV = false // filename IV chaining
    private var _allowHoles = false // allow holes in files (implicit zero blocks)

    private val supportedNameCodecs: Iterable<NameCodecInfo>
        get() = FS.Companion.getSupportedNameCodecs()

    private val supportedDataCodecs: Iterable<DataCodecInfo>
        get() = FS.Companion.getSupportedDataCodecs()

    private fun readCfgElement(cfg: Element) {
        _subVersion = getParam(cfg, "version", 20100713)
        _creator = getParam(cfg, "creator", "")
        dataCodecInfo = loadAlgInfo(cfg, "cipherAlg", supportedDataCodecs, null) as DataCodecInfo?
        nameCodecInfo = loadAlgInfo(cfg, "nameAlg", supportedNameCodecs, null) as NameCodecInfo?
        _keySizeBits = getParam(cfg, "keySize", 0)
        blockSize = getParam(cfg, "blockSize", 0)
        _uniqueIV = getParam(cfg, "uniqueIV", true)
        _chainedNameIV = getParam(cfg, "chainedNameIV", true)
        _externalIVChaining = getParam(cfg, "externalIVChaining", false)
        mACBytes = getParam(cfg, "blockMACBytes", 0)
        mACRandBytes = getParam(cfg, "blockMACRandBytes", 0)
        _allowHoles = getParam(cfg, "allowHoles", true)

        encryptedVolumeKey = getBytes(cfg, "encodedKeyData")
        var size = getParam(cfg, "encodedKeySize", 0)
        require(!(size > 0 && size != encryptedVolumeKey!!.size)) { "Failed decoding key data" }

        salt = getBytes(cfg, "saltData")
        size = getParam(cfg, "saltLen", 0)
        require(!(size > 0 && size != salt!!.size)) { "Failed decoding salt data" }
        kDFIterations = getParam(cfg, "kdfIterations", 0)
        _desiredKDFDuration = getParam(cfg, "desiredKDFDuration", 0)
    }

    private fun getParam(cfg: Element, paramName: String, defaultValue: Int): Int {
        val s = getParam(cfg, paramName, null) ?: return defaultValue
        return s.toInt()
    }

    private fun getParam(cfg: Element, paramName: String, defaultValue: Boolean): Boolean {
        val s = getParam(cfg, paramName, null) ?: return defaultValue
        return "0" != s
    }

    private fun getParam(cfg: Element, paramName: String, defaultValue: String?): String? {
        val nl = cfg.getElementsByTagName(paramName)
        if (nl.length == 0) return defaultValue
        val n = nl.item(0)
        val data = n.textContent
        return data ?: defaultValue
    }

    private fun getBytes(cfg: Element, paramName: String): ByteArray? {
        val encoded = getParam(cfg, paramName, null) ?: return null
        return Base64.decode(encoded, Base64.DEFAULT)
    }

    private fun loadAlgInfo(
        cfg: Element,
        paramName: String,
        supportedAlgs: Iterable<AlgInfo>,
        defaultValue: AlgInfo?
    ): AlgInfo? {
        val nl = cfg.getElementsByTagName(paramName)
        if (nl.length == 0) return defaultValue
        val n = nl.item(0)
        require(n.nodeType == Node.ELEMENT_NODE) { "Wrong document structure" }
        val algName = getParam(n as Element, "name", null)
        requireNotNull(algName) { "Name is not specified for $paramName" }
        val major = getParam(n, "major", 0)
        val minor = getParam(n, "minor", 0)
        for (info in supportedAlgs) {
            if (algName == info.name && info.version1 >= major && info.version2 >= minor) return info.select(
                this
            )
        }
        throw IllegalArgumentException("Unsupported algorithm: $algName major=$major minor=$minor")
    }

    private fun makeCfgElement(doc: Document): Element {
        val cfgEl = doc.createElement("cfg")
        cfgEl.setAttribute("class_id", "0")
        cfgEl.setAttribute("tracking_level", "0")
        cfgEl.setAttribute("version", "20")

        var el = doc.createElement("version")
        cfgEl.appendChild(el)
        el.textContent = _subVersion.toString()

        el = doc.createElement("creator")
        cfgEl.appendChild(el)
        el.textContent = _creator

        el = makeAlgInfoElement(doc, "cipherAlg", dataCodecInfo!!)
        cfgEl.appendChild(el)
        el.setAttribute("class_id", "1")
        el.setAttribute("tracking_level", "0")
        el.setAttribute("version", "0")

        el = makeAlgInfoElement(doc, "nameAlg", nameCodecInfo!!)
        cfgEl.appendChild(el)

        el = doc.createElement("keySize")
        cfgEl.appendChild(el)
        el.textContent = _keySizeBits.toString()

        el = doc.createElement("blockSize")
        cfgEl.appendChild(el)
        el.textContent = blockSize.toString()

        el = doc.createElement("uniqueIV")
        cfgEl.appendChild(el)
        el.textContent = if (_uniqueIV) "1" else "0"

        el = doc.createElement("chainedNameIV")
        cfgEl.appendChild(el)
        el.textContent = if (_chainedNameIV) "1" else "0"

        el = doc.createElement("externalIVChaining")
        cfgEl.appendChild(el)
        el.textContent = if (_externalIVChaining) "1" else "0"

        el = doc.createElement("blockMACBytes")
        cfgEl.appendChild(el)
        el.textContent = mACBytes.toString()

        el = doc.createElement("blockMACRandBytes")
        cfgEl.appendChild(el)
        el.textContent = mACRandBytes.toString()

        el = doc.createElement("allowHoles")
        cfgEl.appendChild(el)
        el.textContent = if (_allowHoles) "1" else "0"

        el = doc.createElement("encodedKeySize")
        cfgEl.appendChild(el)
        el.textContent = encryptedVolumeKey!!.size.toString()

        el = doc.createElement("encodedKeyData")
        cfgEl.appendChild(el)
        el.textContent =
            Base64.encodeToString(encryptedVolumeKey, Base64.DEFAULT)

        el = doc.createElement("saltLen")
        cfgEl.appendChild(el)
        el.textContent = salt!!.size.toString()

        el = doc.createElement("saltData")
        cfgEl.appendChild(el)
        el.textContent = Base64.encodeToString(salt, Base64.DEFAULT)

        el = doc.createElement("kdfIterations")
        cfgEl.appendChild(el)
        el.textContent = kDFIterations.toString()

        el = doc.createElement("desiredKDFDuration")
        cfgEl.appendChild(el)
        el.textContent = _desiredKDFDuration.toString()

        return cfgEl
    }

    private fun makeAlgInfoElement(doc: Document, paramName: String, info: AlgInfo): Element {
        val el = doc.createElement(paramName)

        var el2 = doc.createElement("name")
        el.appendChild(el2)
        el2.textContent = info.name

        el2 = doc.createElement("major")
        el.appendChild(el2)
        el2.textContent = info.version1.toString()

        el2 = doc.createElement("minor")
        el.appendChild(el2)
        el2.textContent = info.version2.toString()

        return el
    }

    private fun makeCreatorString(context: Context?): String {
        if (context == null) return "EDS"
        var verName: String? = ""
        try {
            verName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: NameNotFoundException) {
            showAndLog(context, e)
        }
        return String.format(
            "%s v%s",
            context.getString(R.string.eds),
            verName
        )
    }

    companion object {
        const val CONFIG_FILENAME2: String = "encfs6.xml"
        const val CONFIG_FILENAME: String = '.'.toString() + CONFIG_FILENAME2

        @Throws(IOException::class)
        fun getConfigFilePath(dir: Directory): Path? {
            var p = PathUtil.buildPath(dir.path, CONFIG_FILENAME)
            if (p != null && p.isFile) return p
            p = PathUtil.buildPath(dir.path, CONFIG_FILENAME2)
            return if (p != null && p.isFile) p else null
        }
    }
}

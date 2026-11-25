package com.codewall
import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Свои 3D-модели: компактный файл .pm3 (уже оптимизированная сетка). Встроенные модели: id «m:0..3». */
object ModelStore {
    private const val MAGIC = 0x504D3301
    private fun dir(ctx: Context): File { val d = File(ctx.filesDir, "models"); d.mkdirs(); return d }

    fun save(ctx: Context, name: String, m: Mesh): String {
        val fn = "m" + System.currentTimeMillis() + ".pm3"
        DataOutputStream(BufferedOutputStream(FileOutputStream(File(dir(ctx), fn)))).use { o ->
            o.writeInt(MAGIC); o.writeUTF(name.take(24))
            o.writeInt(m.vertCount)
            for (f in m.v) o.writeFloat(f)
            o.writeInt(m.triCount)
            for (i in m.t) o.writeInt(i)
        }
        return "f:$fn"
    }

    private fun read(f: File, withMesh: Boolean): Pair<String, Mesh?>? = try {
        DataInputStream(BufferedInputStream(FileInputStream(f))).use { i ->
            if (i.readInt() != MAGIC) return@use null
            val name = i.readUTF()
            if (!withMesh) return@use Pair(name, null)
            val vc = i.readInt()
            if (vc < 3 || vc > 400000) return@use null
            val v = FloatArray(vc * 3) { i.readFloat() }
            val tc = i.readInt()
            if (tc < 1 || tc > 400000) return@use null
            val t = IntArray(tc * 3) { i.readInt() }
            for (x in t) if (x < 0 || x >= vc) return@use null
            Pair(name, Mesh(v, t))
        }
    } catch (e: Exception) { null }

    fun list(ctx: Context): List<Pair<String, String>> =
        (dir(ctx).listFiles() ?: emptyArray<File>()).filter { it.name.endsWith(".pm3") }
            .sortedByDescending { it.lastModified() }
            .mapNotNull { f -> read(f, false)?.let { Pair("f:" + f.name, it.first) } }

    fun resolve(ctx: Context, id: String): Mesh? = when {
        id.startsWith("m:") -> Mesh.builtin((id.substring(2).toIntOrNull() ?: 0).coerceIn(0, 3))
        id.startsWith("f:") -> read(File(dir(ctx), File(id.substring(2)).name), true)?.second
        else -> null
    }

    fun nameOf(ctx: Context, id: String): String = when {
        id.startsWith("m:") -> Mesh.BUILTIN_NAMES.getOrElse(id.substring(2).toIntOrNull() ?: 0) { "Модель" }
        else -> read(File(dir(ctx), File(id.substring(2)).name), false)?.first ?: "Модель"
    }

    fun delete(ctx: Context, id: String) { if (id.startsWith("f:")) File(dir(ctx), File(id.substring(2)).name).delete() }
}

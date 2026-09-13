package world.taqwa.app.crash

import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Sink

/**
 * okio has no read-only switch for [okio.fakefilesystem.FakeFileSystem]; this wrapper throws on
 * every write, which is what a full or locked disk does to the store and the crash handler.
 */
class ReadOnlyFileSystem(inner: FileSystem) : ForwardingFileSystem(inner) {
    override fun sink(file: Path, mustCreate: Boolean): Sink = throw IOException("read only")
    override fun createDirectory(dir: Path, mustCreate: Boolean) = throw IOException("read only")
    override fun atomicMove(source: Path, target: Path) = throw IOException("read only")
    override fun delete(path: Path, mustExist: Boolean) = throw IOException("read only")
}

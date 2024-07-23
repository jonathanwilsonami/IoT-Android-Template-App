import com.example.iotandroidtemplateapp.SensorNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class SensorNodeRepository {
    private val apiService = NetworkModule.apiService

    suspend fun sendNodeData(node: SensorNode): Result<Any> {
        return withContext(Dispatchers.IO) {
            try {
                val res = apiService.saveNode(node) // Use saveNode instead of sendNodeData
                Result.success(res)
            } catch (e: HttpException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

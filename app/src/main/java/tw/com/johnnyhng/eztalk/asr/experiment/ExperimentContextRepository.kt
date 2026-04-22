package tw.com.johnnyhng.eztalk.asr.experiment

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

internal interface ExperimentContextRepository {
    suspend fun listScenarios(): List<ExperimentScenario>
}

internal class FirebaseExperimentContextRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ExperimentContextRepository {
    override suspend fun listScenarios(): List<ExperimentScenario> {
        return try {
            firestore.collection("experiment_contexts")
                .get()
                .await()
                .documents
                .map { doc ->
                    ExperimentScenario(
                        id = doc.id,
                        name = doc.getString("name") ?: doc.id,
                        emoji = doc.getString("emoji") ?: "💡",
                        keywords = (doc.get("keywords") as? List<*>)?.map { it.toString() } ?: emptyList(),
                        customInstruction = doc.getString("customInstruction")
                    )
                }
        } catch (e: Exception) {
            // Fallback to defaults if Firestore fails or collection is empty
            defaultScenarios
        }
    }
}

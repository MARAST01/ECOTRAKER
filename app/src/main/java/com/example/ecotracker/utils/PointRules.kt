import com.example.ecotracker.data.model.TransportType

object PointRules {
    fun pointsForTransport(type: TransportType): Int {
        return when (type) {
            TransportType.WALKING -> 12    // tú puedes cambiarlos
            TransportType.BICYCLE -> 10
            TransportType.BUS -> 6
            TransportType.CAR -> 0
            else -> 0
        }
    }
}

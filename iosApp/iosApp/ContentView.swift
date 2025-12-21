import SwiftUI
import Shared

struct ContentView: View {
    @StateObject private var viewModel = AvifConverterViewModel()
    @State private var showResultView = false

    var body: some View {
        NavigationView {
            ZStack {
                UploadView(viewModel: viewModel)

                // Navigate to result view when conversion succeeds
                NavigationLink(
                    destination: resultView,
                    isActive: $showResultView
                ) {
                    EmptyView()
                }
                .hidden()
            }
            .navigationBarHidden(true)
            .onChange(of: viewModel.uiState) { state in
                if case .success = state {
                    showResultView = true
                }
            }
        }
        .navigationViewStyle(StackNavigationViewStyle())
    }

    @ViewBuilder
    private var resultView: some View {
        if case let .success(result, originalImageData) = viewModel.uiState {
            ResultView(
                result: result,
                originalImageData: originalImageData,
                targetMaxSize: viewModel.customParams.maxSize,
                onBack: {
                    viewModel.resetState()
                    showResultView = false
                }
            )
        } else {
            EmptyView()
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}

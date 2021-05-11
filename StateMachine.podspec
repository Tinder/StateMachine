Pod::Spec.new do |s|
  s.name             = 'StateMachine'
  s.version          = '0.0.1'
  s.summary          = 'A state machine library in Swift'
  s.homepage         = 'https://github.com/Tinder/StateMachine'
  s.license          = { :type => 'BSD 3-Clause "New" or "Revised" License', :file => 'LICENSE' }
  s.author           = { 'Tinder' => 'info@gotinder.com' }
  s.source           = { :git => 'https://github.com/Tinder/StateMachine.git', :tag => s.version.to_s }

  s.osx.deployment_target = '10.13'
  s.ios.deployment_target = '11.0'
  s.tvos.deployment_target = '11.0'
  s.watchos.deployment_target = '5.0'

  s.source_files = 'Swift/Sources/StateMachine/**/*'

  s.frameworks = 'Foundation'
end
